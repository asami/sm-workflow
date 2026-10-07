package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model}

/** Producer behavior: compile/run with the admitted Cozy Scala2.12 classpath,
  * not the Scala3 application ABI's standalone-composite bootstrap.
  *
  * @since Oct.  4, 2026
  * @version Oct.  5, 2026
  */
object SplitPhaseCmlDerivationProbe {
  def main(args: Array[String]): Unit = {
    require(args.length == 1, "Expected one actual CML source")
    // Given the actual current application CML, normalized by its real producer.
    val model = Model.parse(KaleidoxConfig.default,
      new String(Files.readAllBytes(Paths.get(args(0))), StandardCharsets.UTF_8))
    require(model.errors.isEmpty, model.errors.mkString(" | "))
    val selected = CompositeStateMachineCml.workflowDefinitions(model).filter(_.identity == "SplitPhaseWorkflow")
    require(selected.size == 1, "Expected one actual SplitPhase Workflow")
    val workflow = selected.head
    require(workflow.version == "split-phase-selected-application-v12", workflow.version)
    val definition = workflow.compositeStateMachine
    val entries = Vector("EntryAssessment", "ProposalAssessment", "SemanticAssessment", "Previewed", "Applied")
    val humans = Vector("Idle", "Awaiting", "Answered", "Deferred", "Cancelled")
    // When every reachable entry/Human configuration is projected from normalized derivations.
    val actual = for (entry <- entries; human <- humans) yield {
      val configuration = Map("entry" -> entry, "semantic" -> "Awaiting", "partition" -> "Awaiting",
        "scope" -> "Awaiting", "reasoning" -> "Awaiting", "human" -> human, "reasoningHuman" -> "Idle")
      val states = definition.derivations.filter(_.configuration.bindings.map(b => b.role -> b.state).toMap == configuration).map(_.state)
      (entry, human, states)
    }
    val applying = humans.map { human =>
      val configuration = Map("entry" -> "ApplyingDocuments", "semantic" -> "Awaiting", "partition" -> "Awaiting",
        "scope" -> "Awaiting", "reasoning" -> "Awaiting", "human" -> human, "reasoningHuman" -> "Idle")
      definition.derivations.filter(_.configuration.bindings.map(b => b.role -> b.state).toMap == configuration).map(_.state)
    }
    require(applying.forall(_ == Vector("ApplyingDocuments")), s"Application intent derivations: $applying")
    // Then each pair is unique and retained Human state never reopens a terminal entry.
    require(actual.size == 25, "Expected all25 pairs")
    actual.foreach { case (entry, human, states) =>
      val expected = if (entry == "Previewed" || entry == "Applied") entry
        else if (human == "Awaiting") "HumanPending"
        else if (human == "Answered") "HumanAnswered"
        else if (human == "Deferred") "HumanDeferred"
        else if (human == "Cancelled") "HumanCancelled"
        else Map("EntryAssessment" -> "EntryPending", "ProposalAssessment" -> "ProposalPending",
          "SemanticAssessment" -> "SemanticPending")(entry)
      require(states == Vector(expected), s"$entry/$human: $states != $expected")
    }
    // When reasoning Human advances, include retained states of the preceding scope channel.
    for (entry <- entries :+ "ApplyingDocuments"; scopehuman <- humans; human <- humans.tail) {
      val configuration = Map("entry" -> entry, "semantic" -> "Awaiting", "partition" -> "Awaiting",
        "scope" -> "Awaiting", "reasoning" -> "Awaiting", "human" -> scopehuman, "reasoningHuman" -> human)
      val states = definition.derivations.filter(_.configuration.bindings.map(b => b.role -> b.state).toMap == configuration).map(_.state)
      val expected = if (Set("Previewed", "Applied", "ApplyingDocuments").contains(entry)) entry
        else if (Vector(scopehuman, human).contains("Cancelled")) "HumanCancelled"
        else if (Vector(scopehuman, human).contains("Deferred")) "HumanDeferred"
        else Map("Awaiting" -> "HumanPending", "Answered" -> "HumanAnswered")(human)
      // Then every combination is unique; application and stopped Human states dominate.
      require(states == Vector(expected), s"reasoning $entry/$scopehuman/$human: $states != $expected")
    }
    require(definition.derivations.size == 150, "Expected all150 scope/reasoning Human configurations")
    println("accepted workflow=SplitPhaseWorkflow version=split-phase-selected-application-v12 pairs=150 terminalDominance=true")
  }
}
