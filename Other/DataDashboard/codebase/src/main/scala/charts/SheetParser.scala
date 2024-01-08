package charts

import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.ss.usermodel.{Cell, CellType, Row}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy

import java.io.File
import scala.collection.convert.ImplicitConversions.{`iterable AsScalaIterable`, `iterator asScala`}
import scala.language.postfixOps

object SheetParser {

  System.setProperty("hadoop.home.dir", "./apache_util")
  System.setProperty("log4j.configurationFile", "./apache_util/log4j2.xml")
  val police = MissingCellPolicy.RETURN_BLANK_AS_NULL

  def parseTitles(fp: String,
                  sheetNum: Int): Array[Option[String]] = {
    val pkg = OPCPackage.open(new File(fp))
    val sheet = new XSSFWorkbook(pkg).getSheetAt(sheetNum)
    pkg.revert()
    pkg.close()
    val rowCandidates = sheet.rowIterator().toArray
    val leftMostCell: Int = rowCandidates.find(_.nonEmpty).get.getFirstCellNum
    val rightMostCell: Int = rowCandidates.map(_.getLastCellNum).max


    val rowOption = rowCandidates.find(
      // Row is scanned if it contains a number
      r => {
        r.nonEmpty && r.forall(c => c.getCellType == CellType.BLANK || c.getCellType == CellType.STRING)
      })
    if (rowOption.isDefined) {
      rowOption.get.map(cell => cell.toString match {
        case "" => None
        case s: String => Some(s)
      }).toArray
    } else {
      LazyList.continually(None).take(rightMostCell - leftMostCell).toArray
    }

  }

  def parseNumericData(fp: String,
                       sheet: Int,
                       xRange: (Option[Double], Option[Double])): Array[Array[(Double, Double)]] = {


    val rows = getRows(fp, sheet)

    // leftMostCell assumes that x values are on the leftmost column
    val leftMostCell: Int = rows.find(_.nonEmpty).get.getFirstCellNum
    val rightMostCell: Int = rows.map(_.getLastCellNum).max

    val dataRows: Array[Array[Option[Double]]] =
      rows
        .map(row =>
          (leftMostCell to rightMostCell - 1)
            .map(ind => {
              // If a cell is blank, return None. Otherwise, if it contains a number, return Some(Number)
              row.getCell(ind, police) match {
                case null => None
                case c: Cell => { // This catches all other cell types, including formulas
                  Some(c.getNumericCellValue)
                }
              }
            }).toArray
        ).toArray
        .sortBy(_.head)

    var columns = LazyList.continually(Array[Option[Double]]())
      .take(rightMostCell - leftMostCell)
      .toArray

    for (row <- dataRows) {
      row.indices.foreach(index => {
        columns(index) =
          columns(index) :+ row(index)
      })
    }

    columns
      .filter(_.nonEmpty) // Use only non-empty columns
      .tail
      .map(column =>
        (columns.head zip column)
          .filter(pair =>
            (!(xRange._1.isDefined && xRange._1.get > pair._1.get)) &&
              (!(xRange._2.isDefined && pair._1.get > xRange._2.get)) &&
              pair._2.isDefined)
          .map(pair => {
            (pair._1.get, pair._2.get)
          })
      )
  }

  def getRows(fp: String,
              sheetNum: Int): Array[Row] = {
    val pkg = OPCPackage.open(new File(fp))
    val sheet = new XSSFWorkbook(pkg).getSheetAt(sheetNum)
    pkg.revert()
    pkg.close()
    sheet.rowIterator().toArray.filter(
      // Row is scanned if it contains a number or a formula
      r => r.exists(c => c.getCellType == CellType.NUMERIC ||
        c.getCellType == CellType.FORMULA))
  }

  def parseNonNumericData(fp: String,
                          sheet: Int): Array[(String, Double)] = {

    val rows = getRows(fp, sheet)

    val leftMostCell: Int = rows.find(_.nonEmpty).get.getFirstCellNum

    val xArray: Array[String] = rows.map(_.getCell(leftMostCell).getStringCellValue)
    val yArray: Array[Option[Double]] =
      rows
        .map(row => {
          val c = row.getCell(leftMostCell + 1)
          c.getCellType match {
            case CellType.BLANK => None
            case CellType.NUMERIC => Some(c.getNumericCellValue)
            case otherType => throw new Exception(s"Expected cell type: numeric\n Found: ${otherType.toString}")
          }
        })

    xArray.zip(yArray)
      .filter(_._2.isDefined)
      .map(pair => (pair._1, pair._2.get))
  }
}
