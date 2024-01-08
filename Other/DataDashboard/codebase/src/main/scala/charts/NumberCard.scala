package charts

import org.apache.hadoop.shaded.org.eclipse.jetty.websocket.client.NoOpEndpoint

import java.awt.{Color, Graphics2D, Point}

class NumberCard(
                  name: Option[String],
                  dim: (Int, Int),
                  loc: Point,
                  col: Color,
                  fp: Option[String],
                  isOnline: Boolean,
                  ID: Int,
                  val value: Double
                ) extends Chart {

  var mFilePath: Option[String] = fp
  val sourceFileIsOnline: Boolean = false
  val chartID = ID
  val minSize = (300, 300)
  var mDimensions = dim
  var mLocation = loc
  var mColor = col
  var mIsVisible = true

  var titles = Map[String, String]()
  titles = titles + (name match {
    case Some(title)  => ("mainTitle", title)
    case None         => ("mainTitle", "Chart " + ID.toString)
  })

  def updateData() = ???

  def paintChart(g: Graphics2D): Unit = ???

  def cloneChart(): Chart = {
    new NumberCard(name, dim, loc, col, None, false, chartID, value)
  }
  def cloneChartWithData(): Chart = {
    new NumberCard(name, dim, loc, col, Some("src/main/scala/charts/util/empty.xlsx"), false, chartID, value)
  }

}