package charts

import java.awt.{Color, Graphics2D, Point}
import scala.swing._
import java.awt.Point
import java.awt.geom.Rectangle2D

trait Chart extends Component {

  // Possible keys: mainTitle, xTitle, yTitle
  //var filePath: Option[String] // Returns None if no address has been assigned
  val chartID: Int // Is unique for all charts
  var mDimensions: (Int, Int)
  var mLocation: Point
  var mFilePath: Option[String]
  var mIsVisible: Boolean
  var titles: Map[String, String]
  val minSize: (Int, Int)
  var isHighlighted: Boolean = false

  def corners = Array(
    new Point(xMin, yMin),
    new Point(xMin, yMax),
    new Point(xMax, yMax),
    new Point(xMax, yMin)
  )

  def isVisible: Boolean = mIsVisible

  def xMax: Int = xMin + width

  def width = mDimensions._1

  def xMin: Int = location.x

  def yMax: Int = yMin + height

  def height = mDimensions._2

  def yMin: Int = location.y

  override def location = mLocation // Location of top-left corner

  def containsPoint(p: Point) = masterRectangle.getBounds2D.contains(p)

  def masterRectangle = new Rectangle2D.Double(mLocation.x, mLocation.y, mDimensions._1, mDimensions._2)

  def xDistance(another: Chart) = {
    math.min(math.abs(this.xMax - another.xMin), math.abs(this.xMin - another.xMax))
  }

  def yDistance(another: Chart) = {
    math.min(math.abs(this.yMax - another.yMin), math.abs(this.yMin - another.yMax))
  }

  // Returns distance between mid points of two charts
  def distance(another: Chart) =
    (new Point( this.mLocation.x + width / 2, this.mLocation.y + height / 2)).distance(
      new Point( another.mLocation.x + width / 2, another.mLocation.y + height / 2))

  def directionTo(another: Chart) = {
    if (xDistance(another) < yDistance(another)) {
      if (another.xMax <= this.xMin) {
        0
      } else {
        2
      }
    } else {
      if (this.yMax <= another.yMin) {
        1
      } else {
        3
      }
    }
  }

  def getDraggedCorner(p: Point): Option[Int] = {
    val wigRoom = 15

    def bounds(coord: Int): (Int, Int) = (coord - wigRoom, coord + wigRoom)

    def pIsWithinBounds(bPoint: Point): Boolean =
      bounds(bPoint.x)._1 <= p.x && p.x <= bounds(bPoint.x)._2 &&
        bounds(bPoint.y)._1 <= p.y && p.y <= bounds(bPoint.y)._2

    corners.indices.find(ind => pIsWithinBounds(corners(ind)))
  }

  def dragCorner(p: Point, draggedCorner: Int): Unit = {

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
  def updateData(): Unit

  def dimensions: (Int, Int) = mDimensions

  def move(offset: (Int, Int)): Unit = {
    mLocation.translate(offset._1, offset._2)
  }

  def moveTo(p: Point) = {
    mLocation = p
  }

  def setVisibility(isVisible: Boolean): Unit = {
    mIsVisible = isVisible
  }

  def equals(other: Chart): Boolean = this.chartID == other.chartID

  def paintChart(g: Graphics2D): Unit

  def cloneChart(): Chart

  def cloneChartWithData(): Chart

  // def parseData: Either[Array[Array[Double]], Map[String, Double]] // Return type is either Array[Array[Double]] or Map[String, Double]
  /** Ask assistant about this! */
}
