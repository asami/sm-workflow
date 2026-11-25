/**
 * @since   Nov. 25, 2026
 * @version Nov. 24, 2026
 */
package org.simplemodeling.textus.ai.runtime

import java.net.URI
import java.time.{Instant, ZoneOffset}
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.ai.runner.{AiChatRequest, AiChatResponse, AiGenerateRequest, AiGenerateResponse, AiRecordRequest, AiRecordResponse, AiRunner}
import org.simplemodeling.textus.ai.provider.openai.{OpenAiBoundedTaskInvoker, OpenAiLedgerBackedAllowance, OpenAiRequestSizingVerifier, OpenAiRuntimeConfig}

/** Synthetic transport and verified synthetic sizing; never constructs a network driver. */
object Phase251OfflineAllowanceFixture {
  val config = OpenAiRuntimeConfig(endpoint = URI.create("https://api.openai.com"),
    apiKey = "fixture-key", model = "gpt-5.6-terra")
  private val verifier = new OpenAiRequestSizingVerifier {
    def verify(endpoint: String, model: String, format: String, tokensPerByte: Long,
        overheadTokens: Long, provenance: String): Boolean =
      endpoint == "https://api.openai.com" && model == config.model &&
        format == "chat-completions-bounded-record-v1" && tokensPerByte == 1 &&
        Set(100L, 600000L).contains(overheadTokens) && provenance == "synthetic-fixture-only"
  }

  def allocation(dailyLimit: Long, credit: Boolean, dailyIneligible: Boolean, overhead: Long = 100L, expiredCredit: Boolean = false, claimedModel: String = config.model): String = {
    val now = Instant.now()
    val window = Vector("validFrom" -> Json.fromString(now.minusSeconds(60).toString),
      "expiresAt" -> Json.fromString(now.plusSeconds(300).toString),
      "provenance" -> Json.fromString("fixture-evidence"))
    val common = Vector("organization" -> Json.fromString("org-fixture"), "project" -> Json.fromString("proj-fixture"),
      "origin" -> Json.fromString("https://api.openai.com"), "model" -> Json.fromString(claimedModel),
      "modelPool" -> Json.fromString("openai-daily-group-2"), "inputTokenCeiling" -> Json.fromLong(1050000),
      "maxOutputTokens" -> Json.fromInt(32), "fullCostMicros" -> Json.fromLong(5250576),
      "reviewMaxOutputTokens" -> Json.fromInt(32), "repairMaxOutputTokens" -> Json.fromInt(24),
      "sizing" -> Json.obj("model" -> Json.fromString(config.model),
        "format" -> Json.fromString("chat-completions-bounded-record-v1"), "tokensPerByte" -> Json.fromLong(1),
        "overheadTokens" -> Json.fromLong(overhead), "provenance" -> Json.fromString("synthetic-fixture-only")),
      "daily" -> Json.obj((window ++ Vector("day" -> Json.fromString(now.atZone(ZoneOffset.UTC).toLocalDate.toString),
        "limitTokens" -> Json.fromLong(dailyLimit)))*))
    val creditWindow = if (expiredCredit) Vector("validFrom" -> Json.fromString(now.minusSeconds(600).toString),
      "expiresAt" -> Json.fromString(now.minusSeconds(300).toString), "provenance" -> Json.fromString("fixture-evidence")) else window
    val promotion = Option.when(credit)("credit" -> Json.obj((creditWindow ++ Vector(
      "allocationId" -> Json.fromString("credit-fixture"), "limitMicros" -> Json.fromLong(5250576)))*))
    val ineligible = Option.when(dailyIneligible)("dailyIneligible" -> Json.obj(window*))
    Json.obj((common ++ promotion ++ ineligible)*).noSpaces
  }

  def acceptsModelClaim(allocationJson: String, model: String): Boolean =
    OpenAiLedgerBackedAllowance.parse(allocationJson, config.copy(model = model)).nonEmpty

  final class Transport extends AiTaskManagedPost {
    val calls = new AtomicInteger(0)
    val replies = new ConcurrentLinkedQueue[Either[String, (Int, Option[String], String)]]()
    val bodies = new ConcurrentLinkedQueue[Json]()
    @volatile var replyForBody: Option[Json => String] = None
    @volatile var onPost: () => Unit = () => ()
    override def isQualified: Boolean = true
    def post(body: Json, timeoutMillis: Long, maxResponseBytes: Int): Either[String, (Int, Option[String], String)] = {
      calls.incrementAndGet()
      bodies.add(body)
      onPost()
      Option(replies.poll()).getOrElse(replyForBody.map(f => Right((200, Some("req-repair-fixture"), f(body))))
        .getOrElse(Left("fixture has no queued response")))
    }
  }

  def runner(context: ExecutionContext, allocationJson: String, post: Transport,
      responses: ConcurrentLinkedQueue[AiRecordResponse] = new ConcurrentLinkedQueue[AiRecordResponse](),
      sizingVerified: Boolean = true): AiRunner = {
    val authority = OpenAiLedgerBackedAllowance.parse(allocationJson, config).map(allocation =>
      OpenAiLedgerBackedAllowance.create(config, allocation, context,
        sizingVerifier = Option.when(sizingVerified)(verifier)))
    val binding = new AiBoundedRecordBinding {
      val limits = AiBoundedRecordLimits(10000L, 0L, 65536)
      def prepare(request: AiTaskRequest, deadline: AiTaskDeadline)(using ExecutionContext)
          : Consequence[(Vector[AiTaskCandidate], AiTaskInvoker)] = {
        authority match {
          case Some(value) =>
            val candidates = value.assess(request, deadline.endNanos).toVector
            Consequence.success(candidates -> new OpenAiBoundedTaskInvoker(value, post, 1048576, Some(deadline)))
          case None => Consequence.success(Vector.empty[AiTaskCandidate] -> new AiTaskInvoker {
            def preflight(r: AiTaskRequest, c: AiTaskCandidate): Either[String, AiTaskInvocationCapability] =
              throw new IllegalStateException("Unavailable fixture invoker called")
            def invoke(r: AiTaskRequest, c: AiTaskCandidate): AiTaskDispatch =
              throw new IllegalStateException("Unavailable fixture invoker called")
          })
        }
      }
    }
    new AiRunner {
      def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] =
        Consequence.operationInvalid("Unexpected generate")
      def chat(req: AiChatRequest)(using ExecutionContext): Consequence[AiChatResponse] =
        Consequence.operationInvalid("Unexpected chat")
      def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse] =
        AiBoundedRecordProtocol.runC(req, binding).map { response => responses.add(response); response }
    }
  }
}
