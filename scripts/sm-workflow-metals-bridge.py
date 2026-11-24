#!/usr/bin/python3
"""Finite CNCF recipe for a host-fixed Metals 1.5.3 stdio provider.

The host fixes SM_WORKFLOW_METALS_SERVER to an absolute executable wrapper.
Requests contain data only. No executable, arguments, cwd or build is accepted.
"""
import json
import os
from pathlib import Path
import selectors
import signal
import subprocess
import sys
import tempfile
import time

MAX_BYTES = 1048576
MAX_FRAMES = 256
SERVER_IDENTITY = "scala-metals-1-5-3"


class ProtocolError(Exception):
    pass


class DeadlineError(Exception):
    pass


class Channel:
    def __init__(self, process, deadline):
        self.process = process
        self.deadline = deadline
        self.buffer = bytearray()
        self.total = 0
        self.frames = 0
        self.selector = selectors.DefaultSelector()
        self.selector.register(process.stdout, selectors.EVENT_READ, "stdout")
        self.selector.register(process.stderr, selectors.EVENT_READ, "stderr")
        os.set_blocking(process.stdin.fileno(), False)
        os.set_blocking(process.stdout.fileno(), False)
        os.set_blocking(process.stderr.fileno(), False)

    def remaining(self):
        remaining = self.deadline - time.monotonic()
        if remaining <= 0:
            raise DeadlineError()
        return remaining

    def send(self, message):
        body = json.dumps(message, ensure_ascii=True, separators=(",", ":")).encode("utf-8")
        if len(body) > MAX_BYTES:
            raise ProtocolError("outgoing frame bound")
        packet = memoryview(b"Content-Length: " + str(len(body)).encode("ascii") + b"\r\n\r\n" + body)
        with selectors.DefaultSelector() as writable:
            writable.register(self.process.stdin, selectors.EVENT_WRITE)
            while packet:
                if not writable.select(self.remaining()):
                    raise DeadlineError()
                count = os.write(self.process.stdin.fileno(), packet)
                packet = packet[count:]

    def notify(self, method, params):
        self.send({"jsonrpc": "2.0", "method": method, "params": params})

    def receive(self):
        while True:
            boundary = self.buffer.find(b"\r\n\r\n")
            if boundary >= 0:
                if boundary > 4096:
                    raise ProtocolError("header bound")
                headers = self.buffer[:boundary].decode("ascii").split("\r\n")
                lengths = [line.split(":", 1)[1].strip() for line in headers
                           if line.lower().startswith("content-length:")]
                if len(lengths) != 1 or not lengths[0].isascii() or not lengths[0].isdigit():
                    raise ProtocolError("frame length")
                length = int(lengths[0])
                if length < 1 or length > MAX_BYTES:
                    raise ProtocolError("frame bound")
                if len(self.buffer) >= boundary + 4 + length:
                    body = self.buffer[boundary + 4:boundary + 4 + length]
                    del self.buffer[:boundary + 4 + length]
                    self.frames += 1
                    if self.frames > MAX_FRAMES:
                        raise ProtocolError("frame count bound")
                    value = json.loads(body.decode("utf-8"), object_pairs_hook=unique_fields)
                    if not isinstance(value, dict) or value.get("jsonrpc") != "2.0":
                        raise ProtocolError("RPC envelope")
                    return value
            elif len(self.buffer) > 4096:
                raise ProtocolError("header bound")
            events = self.selector.select(self.remaining())
            if not events:
                raise DeadlineError()
            for key, _ in events:
                chunk = os.read(key.fileobj.fileno(), 65536)
                if not chunk:
                    self.selector.unregister(key.fileobj)
                    if key.data == "stdout":
                        raise ProtocolError("provider closed stdout")
                    continue
                self.total += len(chunk)
                if self.total > MAX_BYTES:
                    raise ProtocolError("provider output bound")
                if key.data == "stdout":
                    self.buffer.extend(chunk)

    def request(self, identity, method, params):
        self.send({"jsonrpc": "2.0", "id": identity, "method": method, "params": params})
        while True:
            message = self.receive()
            if "method" in message:
                # A server cannot turn this recipe into workspace/applyEdit,
                # executeCommand, build import, or any other client operation.
                if "id" in message:
                    self.send({"jsonrpc": "2.0", "id": message["id"],
                               "error": {"code": -32601, "message": "Unsupported bounded client operation"}})
            elif message.get("id") == identity and "result" in message and "error" not in message:
                return message["result"]
            else:
                raise ProtocolError("unexpected RPC response")

    def close(self):
        self.selector.close()


def unique_fields(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            raise ProtocolError("duplicate JSON field")
        value[key] = item
    return value


def position(value):
    return (isinstance(value, dict) and set(value) == {"line", "character"}
            and all(type(item) is int and 0 <= item <= MAX_BYTES for item in value.values()))


def admitted_input(value):
    if not isinstance(value, dict) or set(value) != {"schemaVersion", "serverIdentity", "timeoutMillis", "document", "diagnostic"}:
        raise ProtocolError("input fields")
    if value["schemaVersion"] != "sm-workflow.metals-input.v1" or value["serverIdentity"] != SERVER_IDENTITY:
        raise ProtocolError("input identity")
    if type(value["timeoutMillis"]) is not int or not 1 <= value["timeoutMillis"] <= 60000:
        raise ProtocolError("deadline")
    document = value["document"]
    diagnostic = value["diagnostic"]
    if (not isinstance(document, dict) or set(document) != {"uri", "version", "text"}
            or not isinstance(document["text"], str) or "\x00" in document["text"]
            or not isinstance(document["uri"], str) or not document["uri"].startswith("file:")
            or type(document["version"]) is not int or document["version"] < 0):
        raise ProtocolError("document")
    if (not isinstance(diagnostic, dict) or set(diagnostic) != {"range", "code", "message"}
            or not isinstance(diagnostic["code"], str) or not isinstance(diagnostic["message"], str)
            or not isinstance(diagnostic["range"], dict) or set(diagnostic["range"]) != {"start", "end"}
            or not all(position(item) for item in diagnostic["range"].values())):
        raise ProtocolError("diagnostic")
    return value


def translated_actions(actions, local_uri, original_uri):
    if actions is None:
        return None
    if not isinstance(actions, list) or len(actions) > 256:
        raise ProtocolError("action bound")
    # Translate only the single opened URI. All other target URIs are preserved
    # and rejected by Scala's exact ownership and edit-shape admission.
    for action in actions:
        if not isinstance(action, dict) or not isinstance(action.get("edit"), dict):
            continue
        edit = action["edit"]
        changes = edit.get("changes")
        if isinstance(changes, dict) and original_uri in changes:
            raise ProtocolError("provider named candidate URI outside isolated workspace")
        if isinstance(changes, dict) and local_uri in changes:
            changes[original_uri] = changes.pop(local_uri)
        entries = edit.get("documentChanges")
        if isinstance(entries, list):
            for entry in entries:
                if isinstance(entry, dict) and isinstance(entry.get("textDocument"), dict):
                    target = entry["textDocument"]
                    if target.get("uri") == original_uri:
                        raise ProtocolError("provider named candidate URI outside isolated workspace")
                    if target.get("uri") == local_uri:
                        target["uri"] = original_uri
    return actions


def terminate(process):
    # The bridge and its descendants also remain inside CNCF's process tree.
    # Kill the provider group even when its leader already exited.
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        pass
    try:
        process.wait(timeout=0.5)
    except subprocess.TimeoutExpired:
        pass
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    process.wait(timeout=0.5)


def exchange(value, workspace):
    executable = os.environ.get("SM_WORKFLOW_METALS_SERVER", "")
    if not executable or not Path(executable).is_absolute() or not Path(executable).is_file():
        return "unavailable", None
    # No caller path or build definition is materialized. The isolated file is
    # the entire input. Server-driven build imports and edits are unsupported.
    document = value["document"]
    path = workspace / "Main.scala"
    path.write_text(document["text"], encoding="utf-8")
    uri = path.as_uri()
    deadline = time.monotonic() + value["timeoutMillis"] / 1000.0
    process = subprocess.Popen([executable], cwd=workspace, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, start_new_session=True)
    channel = Channel(process, deadline)
    initialized = False
    opened = False
    result = None
    status = "incompatible"
    try:
        initialized_result = channel.request(1, "initialize", {
            "processId": os.getpid(), "rootUri": workspace.as_uri(),
            "workspaceFolders": [{"uri": workspace.as_uri(), "name": "bounded-repair"}],
            "capabilities": {"general": {"positionEncodings": ["utf-16"]},
                             "workspace": {"applyEdit": False, "workspaceEdit": {"documentChanges": True}},
                             "textDocument": {"codeAction": {"codeActionLiteralSupport": {
                                 "codeActionKind": {"valueSet": ["quickfix"]}}}}},
            "initializationOptions": {"statusBarProvider": "off", "isHttpEnabled": False}})
        initialized = True
        capabilities = initialized_result.get("capabilities") if isinstance(initialized_result, dict) else None
        if (not isinstance(capabilities, dict) or capabilities.get("positionEncoding", "utf-16") != "utf-16"
                or not (capabilities.get("codeActionProvider") is True
                        or isinstance(capabilities.get("codeActionProvider"), dict))):
            raise ProtocolError("unsupported capabilities")
        channel.notify("initialized", {})
        channel.notify("textDocument/didOpen", {"textDocument": {
            "uri": uri, "version": document["version"], "languageId": "scala", "text": document["text"]}})
        opened = True
        result = channel.request(2, "textDocument/codeAction", {
            "textDocument": {"uri": uri}, "range": value["diagnostic"]["range"],
            "context": {"diagnostics": [value["diagnostic"]], "only": ["quickfix"]}})
        result = translated_actions(result, uri, document["uri"])
        status = "completed"
    except DeadlineError:
        status = "timed-out"
    except (ProtocolError, ValueError, UnicodeError, OSError):
        status = "incompatible"
    finally:
        # Each operation consumes the same total deadline; shutdown never
        # extends a timed-out request. Forced termination is still mandatory.
        try:
            if opened:
                channel.notify("textDocument/didClose", {"textDocument": {"uri": uri}})
            if initialized:
                channel.request(3, "shutdown", None)
                channel.notify("exit", {})
                process.stdin.close()
                if process.wait(timeout=min(0.5, channel.remaining())) != 0:
                    raise ProtocolError("provider exit")
        except (DeadlineError, ProtocolError, ValueError, UnicodeError, OSError, subprocess.TimeoutExpired):
            if status == "completed":
                status = "incompatible"
        finally:
            channel.close()
            terminate(process)
            for pipe in (process.stdin, process.stdout, process.stderr):
                pipe.close()
    return status, result if status == "completed" else None


def main():
    status, actions = "incompatible", None
    try:
        raw = sys.stdin.buffer.read(MAX_BYTES + 1)
        if len(raw) > MAX_BYTES:
            raise ProtocolError("input bound")
        value = admitted_input(json.loads(raw.decode("utf-8"), object_pairs_hook=unique_fields))
        with tempfile.TemporaryDirectory(prefix="metals-", dir=Path.cwd()) as directory:
            status, actions = exchange(value, Path(directory).resolve())
    except FileNotFoundError:
        status = "unavailable"
    except (ProtocolError, ValueError, UnicodeError, OSError):
        status = "incompatible"
    output = json.dumps({"schemaVersion": "sm-workflow.metals-output.v1", "serverIdentity": SERVER_IDENTITY,
                         "status": status, "actions": actions}, ensure_ascii=True, separators=(",", ":"))
    if len(output.encode("utf-8")) > MAX_BYTES:
        output = json.dumps({"schemaVersion": "sm-workflow.metals-output.v1", "serverIdentity": SERVER_IDENTITY,
                             "status": "incompatible", "actions": None}, separators=(",", ":"))
    print(output)


if __name__ == "__main__":
    main()
