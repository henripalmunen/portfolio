package ui

import charts.{BarChart, Chart, LineChart, PieChart}
import io.circe.syntax._
import io.circe._
import io.circe.generic.semiauto._

import java.awt.Point
import scala.swing.Dimension

object SaveFileManager {

  def saveToLine(s: LineChartSave): LineChart = {
    new LineChart(
      Array(s.mainTitle, s.xTitle, s.yTitle),
      new Point(s.x, s.y),
      s.fp,
      if (s.tick.isDefined) Some(s.tick.get, s.range.get) else None,
      s.ID,
      s.sheet,
      (s.xLower, s.xHigher),
      (s.width, s.height)
    )
  }

  def saveToBar(s: BarChartSave): BarChart = {
    new BarChart(
      Array(s.mainTitle, s.xTitle, s.yTitle),
      new Point(s.x, s.y),
      s.fp,
      if (s.tick.isDefined) Some(s.tick.get, s.range.get) else None,
      s.ID,
      s.sheet,
      (s.xLower, s.xHigher),
      (s.width, s.height)
    )
  }

  def saveToPie(s: PieChartSave): PieChart = {
    new PieChart(
      s.name,
      new Point(s.x, s.y),
      s.fp,
      s.ID,
      s.sheet,
      (s.width, s.height)
    )
  }

  def chartsAsJson(charts: List[Chart]): io.circe.Json = {
    chartsToSaves(charts).asJson
  }

  def jsonToChartsAlternate(json: Json): List[Chart] = {
    val cursor: HCursor = json.hcursor
    val lines = cursor
      .get[Array[LineChartSave]]("lines")
    lines.toTry.get.map(saveToLine(_)).toList
  }

  def jsonToCharts(json: Json): List[Chart] = {
    savesToCharts(json.as[Saves].toTry.get)
  }

  def lineToSave(c: LineChart): LineChartSave = {
    LineChartSave(
      c.titleOptions.head,
      c.titleOptions(1),
      c.titleOptions(2),
      c.mLocation.x,
      c.mLocation.y,
      c.mFilePath,
      c.tickerAndRange.map(_._1),
      c.tickerAndRange.map(_._2),
      c.chartID,
      c.sheet,
      c.xRange._1,
      c.xRange._2,
      c.width,
      c.height)
  }

  def barToSave(c: BarChart): BarChartSave = {
    BarChartSave(
      c.titleOptions.head,
      c.titleOptions(1),
      c.titleOptions(2),
      c.mLocation.x,
      c.mLocation.y,
      c.mFilePath,
      c.tickerAndRange.map(_._1),
      c.tickerAndRange.map(_._2),
      c.chartID,
      c.sheet,
      c.xRange._1,
      c.xRange._2,
      c.width,
      c.height)
  }

  def pieToSave(c: PieChart): PieChartSave = {
    PieChartSave(
      c.titles.get("mainTitle"),
      c.mLocation.x,
      c.mLocation.y,
      c.mFilePath,
      c.chartID,
      c.sheet,
      c.width,
      c.height
    )
  }

  def chartsToSaves(charts: List[Chart]): Saves = {
    var lines = List[LineChartSave]()
    var bars = List[BarChartSave]()
    var pies = List[PieChartSave]()
    for (chart <- charts) {
      chart match {
        case c: LineChart => {
          lines = lines :+ lineToSave(c)
        }
        case c: BarChart => {
          bars = bars :+ barToSave(c)
        }
        case c: PieChart => {
          pies = pies :+ pieToSave(c)
        }
      }
    }
    Saves(lines, bars, pies)
  }

  def savesToCharts(saves: Saves): List[Chart] = {
    (saves.lines.map(saveToLine(_)) ++
      saves.bars.map(saveToBar(_)) ++
      saves.pies.map(saveToPie(_))).sortBy(_.chartID)
  }

  trait chartSave

  case class Saves(lines: List[LineChartSave],
                   bars: List[BarChartSave],
                   pies: List[PieChartSave])

  case class LineChartSave(mainTitle: Option[String],
                           xTitle: Option[String],
                           yTitle: Option[String],
                           x: Int,
                           y: Int,
                           fp: Option[String],
                           tick: Option[String],
                           range: Option[String],
                           ID: Int,
                           sheet: Int,
                           xLower: Option[Double],
                           xHigher: Option[Double],
                           width: Int,
                           height: Int
                          ) extends chartSave

  case class BarChartSave(mainTitle: Option[String],
                          xTitle: Option[String],
                          yTitle: Option[String],
                          x: Int,
                          y: Int,
                          fp: Option[String],
                          tick: Option[String],
                          range: Option[String],
                          ID: Int,
                          sheet: Int,
                          xLower: Option[Double],
                          xHigher: Option[Double],
                          width: Int,
                          height: Int
                         ) extends chartSave

  case class PieChartSave(name: Option[String],
                          x: Int,
                          y: Int,
                          fp: Option[String],
                          ID: Int,
                          sheet: Int,
                          width: Int,
                          height: Int
                         ) extends chartSave

  implicit val lineEncoder: Encoder[LineChartSave] = deriveEncoder
  implicit val lineDecoder: Decoder[LineChartSave] = deriveDecoder

  implicit val barEncoder: Encoder[BarChartSave] = deriveEncoder
  implicit val barDecoder: Decoder[BarChartSave] = deriveDecoder

  implicit val pieEncoder: Encoder[PieChartSave] = deriveEncoder
  implicit val pieDecoder: Decoder[PieChartSave] = deriveDecoder

  implicit val saveEncoder: Encoder[Saves] = deriveEncoder
  implicit val saveDecoder: Decoder[Saves] = deriveDecoder

}
