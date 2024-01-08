package ui

import charts._
import scala.collection.mutable.Buffer

object Dashboard {

  var charts = Buffer[Chart]()
  var highlightedCharts = Buffer[Chart]()
  var highlightedDataPoints = Buffer[(Int, Int)]()
  var usedURLs = Buffer[String]()

  def createChart(chart: Chart): Unit = {
    charts += chart
  }

  def deleteHighlightedChart(): Unit = {
    charts = charts.filter(chart => !highlightedCharts.contains(chart))
  }

  def highlightSingleChart(maybeChart: Option[Chart]): Unit = {
    this.charts.foreach( _.isHighlighted = false )
    highlightedCharts = maybeChart.toBuffer
    maybeChart.foreach( _.isHighlighted = true )
  }

  def highlightMultipleCharts(charts: Buffer[Chart]): Unit = {
    this.charts.foreach( _.isHighlighted = false )
    highlightedCharts = charts
    charts.foreach( _.isHighlighted = true )
  }

  def basicStats: Option[Map[String, Double]] = ???

  /** Returns keyword-value pairs, e.g. ("Average", 3.402).
   * Is only concerned about one highlighted chart, if 0 or more
   * than 1 is highlighted, returns None
   */

}
