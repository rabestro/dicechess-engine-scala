package dicechess.engine.movegen

import io.circe.generic.auto.*
import io.circe.parser.decode
import java.io.{File, PrintWriter}
import java.net.URLEncoder
import scala.io.Source

/** Executable task to dynamically generate visual documentation for all move generator test cases.
  */
object DocGenerator:

  private def loadTestCases(resourcePath: String): List[MoveGenTestCase] =
    val source = Option(getClass.getClassLoader.getResourceAsStream(resourcePath))
      .map(Source.fromInputStream)
      .getOrElse(throw new IllegalArgumentException(s"Resource not found: $resourcePath"))
    val jsonStr = try source.mkString
    finally source.close()
    decode[List[MoveGenTestCase]](jsonStr) match
      case Right(cases) => cases
      case Left(error)  => throw new RuntimeException(s"Failed to parse $resourcePath: $error")

  private def diceToSymbol(die: Int): String = die match
    case 1 => "⚀ Pawn (1)"
    case 2 => "⚁ Knight (2)"
    case 3 => "⚂ Bishop (3)"
    case 4 => "⚃ Rook (4)"
    case 5 => "⚄ Queen (5)"
    case 6 => "⚅ King (6)"
    case _ => die.toString

  def main(args: Array[String]): Unit =
    println("Starting documentation generation for Dice Chess Test Cases...")

    val suites = List(
      (
        "1-Die Scenarios",
        "movegen/movegen_1_dice.json",
        "Move generator tests with a single die rolled. These represent the fundamental building blocks of legal move filtering."
      ),
      (
        "2-Dice Scenarios",
        "movegen/movegen_2_dice.json",
        "Move generator tests with two dice rolled. These evaluate intermediate micro-move sequences."
      ),
      (
        "3-Dice Scenarios",
        "movegen/movegen_3_dice.json",
        "Move generator tests with all three dice rolled. These verify full turn execution and complete path optimization."
      )
    )

    val sb = new StringBuilder()
    sb.append("---\n")
    sb.append("title: Move Generator Test Cases\n")
    sb.append("description: Visual catalog of expert-vetted test cases for the Dice Chess move generator.\n")
    sb.append("sidebar:\n")
    sb.append("  order: 6\n")
    sb.append("---\n\n")

    sb.append(
      "Dice Chess has a large and complex state space due to multi-move sequences, dice rolls, and path-optimization rules. This page cataloging all our active test cases is generated dynamically to let developers and chess experts visually inspect, verify, and document each scenario.\n\n"
    )
    sb.append(":::tip[Interactive Catalog]\n")
    sb.append(
      "Each test case includes a graphical representation of the position (rendered dynamically), details of the rolled dice, a complete explanation of the mechanics, and the final list of expected legal moves.\n"
    )
    sb.append(":::\n\n")

    for (title, resourcePath, description) <- suites do
      sb.append(s"## $title\n\n")
      sb.append(s"$description\n\n")

      val cases = try loadTestCases(resourcePath)
      catch
        case e: Exception =>
          println(s"Error loading $resourcePath: ${e.getMessage}")
          Nil

      if cases.isEmpty then sb.append("*No test cases loaded.*\n\n")
      else
        for (tc, index) <- cases.zipWithIndex do
          val caseNum   = index + 1
          val caseTitle = tc.description.getOrElse(s"Scenario $caseNum")
          val diceStr   = tc.dice.map(diceToSymbol).mkString(", ")

          sb.append(s"### $caseNum. $caseTitle\n\n")
          sb.append(s"* **FEN:** `${tc.fen}`\n")
          sb.append(s"* **Dice Rolled:** $diceStr\n")

          val movesStr =
            if tc.expectedMoves.isEmpty then "*None (no legal moves)*"
            else tc.expectedMoves.map(m => s"`$m`").mkString(", ")
          sb.append(s"* **Expected Legal Moves:** $movesStr\n\n")

          // Generate Lichess FEN image URL using standard export format
          val cleanFen   = tc.fen.replace(' ', '_')
          val encodedFen = URLEncoder.encode(cleanFen, "UTF-8")
          val imgUrl     =
            s"https://lichess1.org/export/fen.gif?fen=$encodedFen&color=white&theme=brown&piece=cburnett"

          sb.append(s"![Board Position]($imgUrl)\n\n")
          sb.append("---\n\n")

    // Write to Astro docs content folder
    val outputFile = new File("docs/src/content/docs/architecture/move-generation/06-test-cases.md")
    outputFile.getParentFile.mkdirs()

    val pw = new PrintWriter(outputFile)
    try
      pw.print(sb.toString())
      println(s"Successfully generated visual test cases catalog at: ${outputFile.getAbsolutePath}")
    catch
      case e: Exception =>
        println(s"Failed to write to ${outputFile.getAbsolutePath}: ${e.getMessage}")
    finally pw.close()
