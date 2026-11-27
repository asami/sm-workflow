/**
 * @since   Nov. 27, 2026
 * @version Nov. 27, 2026
 */
package org.simplemodeling.textus.ai.runtime

import java.net.URI
import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import cats.~>
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.context.{ExecutionContext, RuntimeContext}
import org.goldenport.cncf.http.UrlConnectionHttpDriver
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.cncf.spi.ai.runner.*
import org.goldenport.datatype.ContentType
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.simplemodeling.textus.ai.provider.openai.*

/** Actual producer binding and ledger, with a finite interpreter that never delegates HTTP. */
object Phase253TwoGroupAllowanceFixture {
  val origin = "https://api.openai.com"
  val config = OpenAiRuntimeConfig(endpoint = URI.create(origin), apiKey = "fixture", model = "gpt-5.6-terra")
  val verifier = new OpenAiGroupAllocationVerifier {
    def verify(organization: String, project: String, model: String, modelPool: String,
      day: LocalDate, limitTokens: Long, externalTokens: Long, validFrom: Instant, expiresAt: Instant,
      allocationProvenance: String, externalProvenance: String, inputTokenCeiling: Long,
      maxOutputTokens: Int): Boolean = organization == "org-fixture" && project == "proj-fixture" &&
        Set(("gpt-5.6-sol", "openai-daily-group-1"), ("gpt-5.6-terra", "openai-daily-group-2"))((model, modelPool)) &&
        externalTokens == 0 && allocationProvenance == "fixture-allocation" && externalProvenance == "fixture-external"
  }
  val sizing = new OpenAiRequestSizingVerifier {
    def verify(endpoint: String, model: String, format: String, tokensPerByte: Long,
      overheadTokens: Long, provenance: String): Boolean = endpoint == origin &&
      Set("gpt-5.6-sol", "gpt-5.6-terra")(model) && format == "chat-completions-bounded-record-v1" &&
      tokensPerByte == 1 && overheadTokens == 100 && provenance == "fixture-sizing"
  }
  def groups(first: Long, second: Long): Vector[OpenAiLedgerBackedAllowance.Group] = {
    val now = Instant.now()
    def group(model: String, pool: String, limit: Long) = Json.obj(
      "organization" -> Json.fromString("org-fixture"), "project" -> Json.fromString("proj-fixture"),
      "origin" -> Json.fromString(origin), "model" -> Json.fromString(model), "modelPool" -> Json.fromString(pool),
      "inputTokenCeiling" -> Json.fromLong(1000000), "maxOutputTokens" -> Json.fromInt(32),
      "reviewMaxOutputTokens" -> Json.fromInt(32), "repairMaxOutputTokens" -> Json.fromInt(24),
      "fullCostMicros" -> Json.fromLong(1000),
      "daily" -> Json.obj("day" -> Json.fromString(now.atZone(ZoneOffset.UTC).toLocalDate.toString),
        "limitTokens" -> Json.fromLong(limit), "validFrom" -> Json.fromString(now.minusSeconds(60).toString),
        "expiresAt" -> Json.fromString(now.plusSeconds(600).toString), "provenance" -> Json.fromString("fixture-allocation"),
        "externalTokens" -> Json.fromLong(0), "externalProvenance" -> Json.fromString("fixture-external")),
      "sizing" -> Json.obj("model" -> Json.fromString(model), "format" -> Json.fromString("chat-completions-bounded-record-v1"),
        "tokensPerByte" -> Json.fromLong(1), "overheadTokens" -> Json.fromLong(100), "provenance" -> Json.fromString("fixture-sizing")))
    val raw = Json.arr(group("gpt-5.6-sol", "openai-daily-group-1", first),
      group("gpt-5.6-terra", "openai-daily-group-2", second)).noSpaces
    OpenAiLedgerBackedAllowance.parseGroups(raw, config, verifier).getOrElse(sys.error("invalid fixture groups"))
  }
  final class Transport {
    val calls = new AtomicInteger()
    val bodies = new ConcurrentLinkedQueue[Json]()
    @volatile var reply: Json => String = _ => throw new IllegalStateException("unexpected HTTP fixture request")
    def context(base: ExecutionContext): ExecutionContext = {
      lazy val active: ExecutionContext = ExecutionContext.withRuntimeContext(base, runtime)
      lazy val unit = new UnitOfWork(active)
      lazy val runtime: RuntimeContext = new RuntimeContext(
        core = RuntimeContext.core("p253-offline", parent = None, observabilitycontext = base.observability,
          httpdriveroption = Some(new UrlConnectionHttpDriver(origin))),
        unitofworksupplier = () => unit,
        unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
          def apply[A](op: UnitOfWorkOp[A]): Consequence[A] = op match {
            case p: UnitOfWorkOp.HttpPost =>
              val body = io.circe.parser.parse(p.body.getOrElse(throw new IllegalStateException("missing body"))).fold(throw _, identity)
              calls.incrementAndGet(); bodies.add(body)
              Consequence.success(HttpResponse.Text(HttpStatus.Ok, ContentType.APPLICATION_JSON, Bag.text(reply(body))))
            case _: UnitOfWorkOp.HttpGet | _: UnitOfWorkOp.HttpPostBag | _: UnitOfWorkOp.HttpPut | _: UnitOfWorkOp.HttpPutBag =>
              Consequence.operationInvalid("Unexpected fixture HTTP operation")
            case other if other.getClass.getSimpleName.startsWith("Http") =>
              Consequence.operationInvalid("Unexpected fixture HTTP operation")
            case other => new UnitOfWorkInterpreter(unit).interpret(other)
          }
        }, commitaction = _ => (), abortaction = _ => (), disposeaction = _ => (), token = "p253-offline")
      active
    }
  }
  def producerSource: java.net.URL = classOf[OpenAiBoundedRecordBinding].getProtectionDomain.getCodeSource.getLocation
  def runner(values: Vector[OpenAiLedgerBackedAllowance.Group]): AiRunner = {
    val binding = new OpenAiBoundedRecordBinding(_ => None, AiBoundedRecordLimits(10000L, 0L, 65536),
      Some(() => Some((config, values, 10, verifier, Some(sizing)))))
    new AiRunner {
      def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] = Consequence.operationInvalid("Unexpected generate")
      def chat(req: AiChatRequest)(using ExecutionContext): Consequence[AiChatResponse] = Consequence.operationInvalid("Unexpected chat")
      def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse] = AiBoundedRecordProtocol.runC(req, binding)
    }
  }
}
