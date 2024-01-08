package charts

import ui.DashboardApp

import java.awt.geom.{Ellipse2D, Line2D, Rectangle2D}
import java.awt.{BasicStroke, Color, Font, Graphics2D, Point}
import java.time.{LocalDateTime, ZoneOffset}
import scala.math.{floor, pow}

abstract class NumericX(
                         val titleOptions: Array[Option[String]],
                         loc: Point,
                         fp: Option[String],
                         var tickerAndRange: Option[(String, String)],
                         ID: Int,
                         var sheet: Int,
                         var xRange: (Option[Double], Option[Double]),
                         dim: (Int, Int)
                       ) extends Chart {

  val chartID = ID
  var nd = new NumericXData(titleOptions, fp, tickerAndRange, sheet, xRange)
  var mFilePath: Option[String] = fp
  var mDimensions = dim
  var mLocation = loc
  var mIsVisible = true
  var titles = nd.titles
  if (titleOptions.head.isDefined) titles = titles + ("mainTitle" -> titleOptions.head.get)
  if (titleOptions(1).isDefined) titles = titles + ("xAxisTitle" -> titleOptions(1).get)
  if (titleOptions(2).isDefined) titles = titles + ("yAxisTitle" -> titleOptions(2).get)
  var colorAtInd = Array(
    (69, 23, 234),
    (242, 30, 28),
    (35, 108, 78),
    (242, 101, 28),
    (151, 31, 123),
    (28, 39, 152),
    (35, 108, 78),
    (152, 28, 39),
    (255, 206, 49),
    (0, 0, 0)
  ).map(tuple => new Color(tuple._1, tuple._2, tuple._3))


  override def dragCorner(p: Point, draggedCorner: Int): Unit = {
    /** draggedCorner is an Int between 0 and 3, representing corners in
     * counterclockwise orientation. Top-left is 1:
     * 0 - 3
     * |   |
     * 1 - 2
     */
    mDimensions = draggedCorner match {
      case 0 => (xMax - p.x, yMax - p.y)
      case 1 => (xMax - p.x, p.y - yMin)
      case 2 => (p.x - xMin, p.y - yMin)
      case 3 => (p.x - xMin, yMax - p.y)
    }
    draggedCorner match {
      case 0 => this.moveTo(p)
      case 1 => this.moveTo(new Point(p.x, yMin))
      case 2 =>
      case 3 => this.moveTo(new Point(xMin, p.y))
    }
  }

  def paintChart(g: Graphics2D) = {

    /** This table outlines the ratio of the gap between a charts
     * horizontal lines and the most significant digit of the _difference_
     * of the largest value and the smallest value in the dataset (First
     * digit on the left and gap on the right).
     * For example, if the difference is 4.5 * 10^4, the gap between horizontal
     * lines is 0.5 * 10^4 = 5000.
     * */

    val normalStroke = g.getStroke
    val normalFont = g.getFont
    val rectThickness = if (isHighlighted) 4 else 1

    // Draw main title
    if (titles.keys.toArray.contains("mainTitle")) {
      g.setColor(Color.BLACK)
      g.setFont(new Font("Arial", Font.PLAIN, 20))
      val main = titles("mainTitle").trim.take(34)
      g.drawString(main, (width / 2 - main.length * 4.5).toInt, 40)
      g.setFont(normalFont)
    }

    val dashed = new BasicStroke(
      1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, Array[Float](3), 0)

    g.setColor(Color.LIGHT_GRAY)
    g.setStroke(new BasicStroke(rectThickness))
    g.draw(new Rectangle2D.Double(0, 0, width, height))
    g.setStroke(dashed)

    val os = 4
    g.setColor(new Color(110, 40, 40))
    if (tickerAndRange.isDefined) {
      g.draw(new Rectangle2D.Double(os, os, width - os * 2, height - os * 2))
    }
    g.setStroke(normalStroke)
    g.setColor(Color.LIGHT_GRAY)
    //g.draw(dataArea)

    drawDataFrame(g)

  }

  def drawDataFrame(g: Graphics2D) = {

    val ymin = mLocation.y
    val xmin = mLocation.x
    val defaultFont = g.getFont
    val boldedFont = new Font("Arial", Font.BOLD, 14)
    val biggerFont = new Font("Arial", Font.PLAIN, 16)
    // Before lines are rendered, translate g to the bottom left of dataArea
    g.translate(dataArea.getMinX, dataArea.getMaxY)

    // Do all the rendering for data area

    val dashed = new BasicStroke(
      2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, Array[Float](5), 0)

    // Draw vertical and horizontal lines
    for (horLine <- nd.horLines) {
      val startTranslated = translateCoords((nd.firstVerLine, horLine))
      val endTranslated = translateCoords((nd.lastVerLine, horLine))
      g.draw(new Line2D.Double(
        startTranslated._1, startTranslated._2, endTranslated._1, endTranslated._2))
      // Draw horizontal line title
      val horLineTitle = {
        if (-4 <= nd.powerOfTenY && nd.powerOfTenY <= 4) {
          if (tickerAndRange.isDefined) (floor(horLine * 100) / 100).toString.take(7) else horLine.toString.take(7)
        } else
          ((horLine / pow(10, nd.powerOfTenY) * 100).floor / 100).toString + "e" + nd.powerOfTenY.toInt
      }
      val horizontalLineTitleX = (-horLineTitle.length * 8) - 4
      val horizontalLineTitleY = translateCoords(0, horLine)._2.toInt + 4
      g.setColor(Color.BLACK)
      g.drawString(horLineTitle, horizontalLineTitleX, horizontalLineTitleY)
      g.setColor(Color.LIGHT_GRAY)

    }

    for (verLine <- nd.verLines) {

      val startTranslated = translateCoords((verLine, nd.firstHorLine))
      val endTranslated = translateCoords((verLine, nd.lastHorLine))
      g.draw(new Line2D.Double(
        startTranslated._1, startTranslated._2, endTranslated._1, endTranslated._2))
      // Draw vertical line title
      val verLineTitle = {
        if (-4 <= nd.powerOfTenY && nd.powerOfTenY <= 4) {

          // Title depends on what period the time is "the first of"
          if (tickerAndRange.isDefined) {
            val dt = LocalDateTime.ofEpochSecond(verLine.toLong, 0, ZoneOffset.ofHours(2))
            if (tickerAndRange.get._2 == "1d") {
              ("" + dt.getHour + "." + ("" + dt.getMinute + "0").take(2)).take(6)
            } else if (dt.getDayOfYear == 1) {
              g.setFont(boldedFont)
              dt.getYear.toString
            } else if (dt.getHour == 0) {
              "" + dt.getDayOfMonth + "." + dt.getMonthValue
            } else {
              ("" + dt.getHour + "." + dt.getMinute).take(6)
            }
          } else {
            verLine.toString.take(5)
          }

        } else
          ((verLine / pow(10, nd.powerOfTenY) * 100).floor / 100).toString + "e" + nd.powerOfTenY.toInt
      }
      val verLineTitleY = 20
      val verLineTitleX =
        (translateCoords(verLine, 0)._1 - (verLineTitle.length - 1) / 2.0 * 8).toInt
      g.setColor(Color.BLACK)
      g.drawString(verLineTitle, verLineTitleX, verLineTitleY)
      g.setColor(Color.LIGHT_GRAY)
      g.setFont(defaultFont)

    }


    // Draw axes
    g.setColor(Color.BLACK)
    // First two define x axis, last two define y
    val axisPoints = Array(
      (nd.firstVerLine, 0.0), (nd.lastVerLine, 0.0), (0.0, nd.firstHorLine), (0.0, nd.lastHorLine))
      .map(translateCoords)
      .map(pair => new Point(pair._1.toInt, pair._2.toInt))
    if (nd.firstHorLine <= 0 && 0 <= nd.lastHorLine) {
      g.draw(new Line2D.Double(axisPoints(0), axisPoints(1)))
    }
    if (nd.firstVerLine <= 0 && 0 <= nd.lastVerLine) {
      g.draw(new Line2D.Double(axisPoints(2), axisPoints(3)))
    }

    // Draw axis titles
    g.setFont(biggerFont)
    if (titles.keys.toArray.contains("xAxisTitle")) {
      val title = titles("xAxisTitle").trim.take(34)
      val titleX = (dataArea.width / 2.0 - title.length / 2.0 * 4.5).toInt
      val titleY = 40
      g.drawString(title, titleX, titleY)
    }
    val defaultAt = g.getTransform

    if (titles.keys.toArray.contains("yAxisTitle")) {

      val title = titles("yAxisTitle").trim.take(24)
      //val ymin = this.mLocation.y
      //val xmin = this.mLocation.x
      val titleX = (-6 * 8) - 4
      val titleY = -dataArea.height.toInt - 20


      g.setFont(biggerFont)
      g.drawString(title, titleX, titleY)
      //g.draw(new Ellipse2D.Double(-this.yMin, this.xMin, 3, 3))
      g.setTransform(defaultAt)

    }
    g.setFont(defaultFont)


    // Reset translation
    g.translate(-dataArea.getMinX, -dataArea.getMaxY)
    g.translate(-mLocation.x, -mLocation.y)
    this.corners.foreach(p => {
      g.draw(new Rectangle2D.Double(p.x - 2, p.y - 2, 4, 4))
    })
    g.translate(mLocation.x, mLocation.y)

  }

  /** This function takes data values (such as x = 3.4s; y = 2.3kV) and returns
   * the corresponding coordinates that Graphics2D can properly use. The coordinates
   * are calculated so that the origin is at the bottom left of dataArea. */

  def translateCoords(pair: (Double, Double)): (Double, Double) = {
    val dataWidth = dataArea.width
    val dataHeight = dataArea.height
    val a = pair._1
    val b = pair._2
    val miX = nd.firstVerLine
    val maX = nd.lastVerLine
    val miY = nd.firstHorLine
    val maY = nd.lastHorLine

    ((dataWidth * (a - miX) / (maX - miX)),
      -(dataHeight * (b - miY) / (maY - miY)))
  }

  def dataArea = new Rectangle2D.Double(80, 60, width - 160, height - 120)

  def inverseTranslate(pair: (Double, Double)): (Double, Double) = {
    val dataWidth = dataArea.width
    val dataHeight = dataArea.height
    val tx = pair._1
    val ty = pair._2
    val miX = nd.firstVerLine
    val maX = nd.lastVerLine
    val miY = nd.firstHorLine
    val maY = nd.lastHorLine

    (tx * (maX - miX) / dataWidth + miX,
      miY - ty * (maY - miY) / dataHeight)
  }

  def cloneChart(): Chart = {
    new BarChart(
      titleOptions, mLocation, None, None, chartID, sheet, xRange, mDimensions)
  }

  def updateData(): Unit = {
    nd = new NumericXData(titleOptions, mFilePath, tickerAndRange, sheet, xRange)
  }

}

class BarChart(
                titleOptions: Array[Option[String]],
                loc: Point,
                fp: Option[String],
                tickAndRan: Option[(String, String)],
                ID: Int,
                sheet: Int,
                xRange: (Option[Double], Option[Double]),
                dim: (Int, Int) = (720, 490)
              ) extends NumericX(titleOptions, loc, fp, tickAndRan, ID, sheet, xRange, dim) {


  val minSize = ((720 * 0.87).toInt, (490 * 0.87).toInt)

  override def paintChart(g: Graphics2D): Unit = {
    super.paintChart(g)
    //val normalStroke = g.getStroke
    drawDataContent(g)

  }

  def drawDataContent(g: Graphics2D): Unit = {
    val normalStroke = g.getStroke
    val normalFont = g.getFont
    g.translate(dataArea.getMinX, dataArea.getMaxY)
    // Render all data
    var index = 0
    val pointSize = 5
    for (dataset <- nd.parsedData) {
      g.setColor(colorAtInd(index % 10))
      g.setStroke(new BasicStroke( //ceil((dataArea.width - 80) / dataset.length).toInt))
        4))

      // Render each point ,
      // Render all lines
      for (point <- dataset) {
        val startTranslated = translateCoords(point._1, nd.firstHorLine)
        val endTranslated = translateCoords(point)
        val (start, end) =
          (new Point(startTranslated._1.toInt, startTranslated._2.toInt),
            new Point(endTranslated._1.toInt, endTranslated._2.toInt))
        g.draw(new Line2D.Double(start, end))
      }

      g.setStroke(normalStroke)
      index += 1
    }
    g.translate(-dataArea.getMinX, -dataArea.getMaxY)
    // Render point info
    if (this.masterRectangle.contains(DashboardApp.latestClick)) {
      val dataPoint = nd.parsedData.flatten.minBy(pair => {
        val point = new Point(translateCoords(pair._1, pair._2)._1.toInt, translateCoords(pair._1, pair._2)._2.toInt)
        point.translate(this.xMin + 80, this.yMin)
      math.abs(
          point.x - DashboardApp.latestClick.x)
      })
      g.setColor(Color.LIGHT_GRAY)
      val title = dataPoint._1.toString + ", " + dataPoint._2
      g.translate(-this.xMin, -this.yMin)
      g.drawString(
        dataPoint._1.toString + ", " + dataPoint._2,
        DashboardApp.screenWidth - 20 - title.length * 8,
        DashboardApp.screenHeight - 80)
      g.translate(this.xMin, this.yMin)
    }
  }

  def cloneChartWithData(): Chart = new BarChart(
    titleOptions, mLocation, mFilePath, tickerAndRange, chartID, sheet, xRange, mDimensions)

}

class LineChart(
                 titleOptions: Array[Option[String]],
                 loc: Point,
                 fp: Option[String],
                 tickAndRan: Option[(String, String)],
                 ID: Int,
                 sheet: Int,
                 xRange: (Option[Double], Option[Double]),
                 dim: (Int, Int) = (720, 490)
               ) extends NumericX(titleOptions, loc, fp, tickAndRan, ID, sheet, xRange, dim) {

  val minSize = ((720 * 0.87).toInt, (490 * 0.87).toInt)
  var additionalSeriesY = Array[Array[Double]]()

  override def paintChart(g: Graphics2D): Unit = {
    super.paintChart(g)
    drawDataContent(g)
  }

  def drawDataContent(g: Graphics2D): Unit = {
    val normalStroke = g.getStroke
    val normalFont = g.getFont
    g.translate(dataArea.getMinX, dataArea.getMaxY)
    // Render all data
    var index = 0
    val pointSize = 5
    for (dataset <- nd.parsedData) {
      g.setColor(colorAtInd(index % 10))

      // Render each point
      for (dataPoint <- dataset) {
        val translatedCoords = translateCoords(dataPoint)
        val coloredDot = new Ellipse2D.Double(
          translatedCoords._1 - pointSize / 2 - 1,
          translatedCoords._2 - pointSize / 2 - 1,
          pointSize,
          pointSize)
        g.draw(coloredDot)
        g.fill(coloredDot)
        val checkedPoint = new Point(DashboardApp.latestClick.x, DashboardApp.latestClick.y)
        checkedPoint.translate(dataArea.getMinX.toInt, dataArea.getMaxY.toInt)
        if (coloredDot.contains(DashboardApp.latestClick)) {
          g.translate(-dataArea.getMinX, -dataArea.getMaxY)
          g.translate(dataArea.getMinX, dataArea.getMaxY)
        }
      }
      // Render all lines
      for (lineEndPoints <- dataset.sliding(2)) if (dataset.length > 1) {
        val translated = lineEndPoints.map(translateCoords)
        val (start, end) =
          (new Point(translated(0)._1.toInt, translated(0)._2.toInt),
            new Point(translated(1)._1.toInt, translated(1)._2.toInt))
        g.draw(new Line2D.Double(start, end))
      }
      // Draw colored dot
      val coloredDotX = dataArea.width + 20
      val coloredDotY = -dataArea.height + 60 + index * 20
      val coloredDot = new Ellipse2D.Double(coloredDotX, coloredDotY, 10, 10)

      g.setStroke(normalStroke)
      index += 1
    }

    g.translate(-dataArea.getMinX, -dataArea.getMaxY)

    // Render point info
    if (this.masterRectangle.contains(DashboardApp.latestClick)) {
      val dataPoint = nd.parsedData.flatten.minBy(pair => {
        val point = new Point(translateCoords(pair._1, pair._2)._1.toInt, translateCoords(pair._1, pair._2)._2.toInt)
        point.translate(this.xMin + 80, this.yMin)
        math.abs(
          point.x - DashboardApp.latestClick.x)
      })
      g.setColor(Color.LIGHT_GRAY)
      val title = dataPoint._1.toString + ", " + dataPoint._2
      g.translate(-this.xMin, -this.yMin)
      g.drawString(
        dataPoint._1.toString + ", " + dataPoint._2,
        DashboardApp.screenWidth - 20 - title.length * 8,
        DashboardApp.screenHeight - 80)
      g.translate(this.xMin, this.yMin)
    }
  }

  def cloneChartWithData(): Chart = {
    new LineChart(titleOptions, mLocation, mFilePath, tickerAndRange, chartID, sheet, xRange, mDimensions)
  }

}
