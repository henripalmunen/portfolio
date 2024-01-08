package charts

import java.awt.{BasicStroke, Color, Font, Graphics2D, Point, TextArea}
import java.awt.geom.{Arc2D, Ellipse2D, Rectangle2D}
import scala.collection.mutable.Buffer
import scala.util.Random._
import scala.math
import scala.util.Try

abstract class NonNumericX(
                            name: Option[String],
                            loc: Point,
                            fp: Option[String],
                            ID: Int,
                            val sheet: Int,
                            dim: (Int, Int)
                          ) extends Chart {

  var mFilePath: Option[String] = fp
  val chartID = ID
  var mDimensions = dim
  var mLocation = loc
  var mIsVisible = true
  var parsedData = Array[(String, Double)]()
  var titles = Map[String, String]()

  if (mFilePath.isDefined) {
    parsedData = SheetParser.parseNonNumericData(fp.get, sheet)
  }
  if (name.isDefined) {
    titles = titles + ("mainTitle" -> name.get)
  }

  var seriesX: Array[String] = parsedData.map(pair => pair._1)

  var seriesY: Array[Double] = parsedData.map(pair => pair._2)
  var proportionalValuesSorted: Array[(String, Double)] = getPVS

  def updateData(): Unit = {
    parsedData = {
      require(fp.isDefined)
      SheetParser.parseNonNumericData(fp.get, sheet)
    }
    seriesX = parsedData.map(pair => pair._1)
    seriesY = parsedData.map(pair => pair._2)
    proportionalValuesSorted = getPVS
  }

  def getPVS: Array[(String, Double)] = {

    val sorMap: Array[(String, Double)] = parsedData.map(pair => (pair._1, pair._2 / seriesY.sum))
      .sortBy(pair => pair._2)

    if (sorMap.length > 9) {
      (Array(("Other", sorMap.dropRight(9).map(pair => pair._2).sum)) ++
        sorMap.takeRight(9))
        .reverse
    } else {
      sorMap.reverse
    }

  }

  def cloneChart(): Chart = {
    new PieChart(
      name, mLocation, None, chartID, sheet, mDimensions)
  }


  /** Maps values to their ratio with sum of all values */

}

class PieChart(
                name: Option[String],
                loc: Point,
                fp: Option[String],
                ID: Int,
                sheet: Int,
                dim: (Int, Int) = (610, 490)
              ) extends NonNumericX(name, loc, fp, ID, sheet, dim) {

  val minSize = ((610 * 0.87).toInt, (490 * 0.87).toInt)

  var colorAtInd = Array(
    (69, 174, 178),
    (242, 101, 28),
    (83, 36, 30),
    (153, 153, 153),
    (151, 31, 123),
    (28, 39, 152),
    (35, 108, 78),
    (152, 28, 39),
    (255, 206, 49),
    (0, 0, 0)
  ).map(tuple => new Color(tuple._1, tuple._2, tuple._3))

  override def dragCorner(point: Point, draggedCorner: Int): Unit = {

    /** draggedCorner is an Int between 0 and 3, representing corners in
     * counterclockwise orientation. Top-left is 0:
     * 0 - 3
     * |   |
     * 1 - 2
     */

    val p = draggedCorner match {
      // This keeps a chart's dimensions (ratio) locked
      case 0 => {
        val c = math.max(xMin - point.x, yMin - point.y)
        new Point(xMin - c, yMin - c)
      }
      case 1 => {
        val c = math.max(xMin - point.x, point.y - yMax)
        new Point(xMin - c, yMax + c)
      }
      case 2 => {
        val c = math.max(point.x - xMax, point.y - yMax)
        new Point(xMax + c, yMax + c)
      }
      case 3 => {
        val c = math.max(point.x - xMax, yMin - point.y)
        new Point(xMax + c, yMin - c)
      }

    }
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

  def paintChart(g: Graphics2D): Unit = {

    val normalStroke = g.getStroke
    val rectThickness = isHighlighted match {
      case false => 1
      case true => 4
    }
    g.setColor(Color.LIGHT_GRAY)
    g.setStroke(new BasicStroke(rectThickness))
    g.draw(new Rectangle2D.Double(0, 0, width, height))
    g.setStroke(normalStroke)
    // All angles are in degrees by default

    val diameter = Math.min(0.65 * width, 0.65 * height).toInt
    val maxPercentage = 3 // Set max percentage where percentageTitle is rendered


    val numberOfArcs = seriesX.length
    val angleExtents: Array[Double] = {
      proportionalValuesSorted.map(p => p._2)
        .map(frac => frac * 360).toArray
    }
    val startAngles =
      angleExtents.scan(0.0)((x: Double, y: Double) => x + y).dropRight(1)

    // Coordinates for the top-left corner of pie
    val pieX = ((width - diameter) / 4.4).toInt
    val pieY = ((height - diameter) / 1.4).toInt

    val pieCenter: Point = new Point(pieX + diameter / 2, pieY + diameter / 2)
    val coloredDotX = pieCenter.x + diameter * 0.8

    val segments = Buffer[Arc2D.Double]()

    var index = 0
    for (anglePair <- startAngles zip angleExtents) {

      // Set appropriate color
      g.setColor(colorAtInd(index % 10))

      // Draw segment
      val segment = new Arc2D.Double(pieX, pieY, diameter, diameter,
        -anglePair._1 + 90, -anglePair._2, 2)
      segments += segment
      g.draw(segment)
      g.fill(segment)

      // Draw colored dot
      val coloredDotY = pieCenter.y - diameter / 2.5 + index * 20
      val coloredDot = new Ellipse2D.Double(coloredDotX, coloredDotY, 10, 10)
      g.draw(coloredDot)
      g.fill(coloredDot)

      // Draw segment title next to dot
      val segmentTitle = proportionalValuesSorted(index)._1.trim
      g.drawString(segmentTitle.take(20) + (if (segmentTitle.length > 20) "." else ""),
        coloredDotX.toInt + 16, coloredDotY.toInt + 10)

      // Draw percentage title (if percentage is over 5%)
      g.setColor(Color.BLACK)
      val textAngle =
        (anglePair._1 + anglePair._2 / 2 - 90)
      val textDistance =
        (0.60 * diameter).toInt - (math.pow(math.sin((textAngle + 90) / 360 * 2 * math.Pi), 1)) * 16
      val textX =
        pieCenter.x + (textDistance * math.cos(textAngle / 360 * 2 * math.Pi)).toInt
      val textY =
        pieCenter.y + (textDistance * math.sin(textAngle / 360 * 2 * math.Pi)).toInt
      val percentage =
        math.round(proportionalValuesSorted.map(p => p._2).toBuffer(index) * 1000.0) / 10.0
      val percentageTitle =
        percentage.toString + "%"
      if (percentage > maxPercentage) {
        g.drawString(percentageTitle, textX, textY)
      }
      index += 1
    }

    // Draw main title
    if (titles.keys.toArray.contains("mainTitle")) {
      g.setColor(Color.BLACK)
      g.setFont(new Font("Arial", Font.PLAIN, 20))
      g.drawString(titles("mainTitle").take(56), (width / 2 - titles("mainTitle").length * 4.5).toInt, 40)
    }
    g.translate(-mLocation.x, -mLocation.y)
    this.corners.foreach(p => {
      g.draw(new Rectangle2D.Double(p.x - 2, p.y - 2, 4, 4))
    })
    g.translate(mLocation.x, mLocation.y)

  }

  def cloneChartWithData(): Chart = {
    new PieChart(name, mLocation, mFilePath, ID, sheet, mDimensions)
  }

}
