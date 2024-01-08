import org.junit.Test
import org.junit.Assert._
import charts.SheetParser._

class UnitTests {

  def asStr[T](ref: Array[Array[T]], tested: Array[Array[T]]): String =
    "\nExpected: " + ref.map(_.mkString("Array(", ", ", ")")).mkString("\n") + "\n" +
      "Actual: " + tested.map(_.mkString("Array(", ", ", ")")).mkString("\n")

  def twoDimArraysAreEqual[T](a: Array[Array[T]], b: Array[Array[T]]): Boolean = {
    a.map(_.mkString("Array(", ", ", ")")).mkString("Array(", ", ", ")") ==
      b.map(_.mkString("Array(", ", ", ")")).mkString("Array(", ", ", ")")
  }

  @Test def testNumericParse1(): Unit = {
    val tested = parseNumericData(
      "src/test/scala/SheetParserTest.csv",
      0,
      (Some(1), Some(10000000))
    )
    val reference = Array(
      Array(
        (1.0, 0.1),
        (2.0, 0.1),
        (6.0, 0.1)
      ),
      Array(
        (1.0, 0.1),
        (2.0, 0.1),
        (6.0, 0.1)
      )
    )

    assertTrue(asStr(reference, tested),
      twoDimArraysAreEqual(reference, tested))
  }

  @Test def testNonNumericParse1(): Unit = {
    val tested = Array(parseNonNumericData(
      "src/test/scala/SheetParserTest.csv",
      0
    ))
    val reference = Array(
      Array(
        ("1", 0.1),
        ("2", 0.1),
        ("6", 0.1)
      )
    )

    assertTrue(asStr(reference, tested),
      twoDimArraysAreEqual(reference, tested))
  }

}
