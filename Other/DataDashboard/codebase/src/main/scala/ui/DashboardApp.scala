package ui

import charts._
import io.circe.Json
import io.circe.parser._

import java.awt.geom.Rectangle2D
import scala.swing.{Action, BoxPanel, Dialog, Dimension, FileChooser, FlowPanel, Graphics2D, Label, MainFrame, Menu, MenuBar, MenuItem, Orientation, Panel, SimpleSwingApplication}
import java.awt.{BasicStroke, Color, Font, Point, RenderingHints}
import java.io.{File, FileWriter}
import javax.swing.UIManager
import scala.collection.mutable.Buffer
import scala.swing.event.{FocusLost, KeyPressed, MouseDragged, MouseEvent, MousePressed, MouseReleased}
import scala.util.{Failure, Success, Try}

import javax.swing.filechooser.FileNameExtensionFilter
import scala.swing.Swing.EmptyBorder


object DashboardApp extends SimpleSwingApplication {


  UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName)

  val test1 = "5d"

  val db = Dashboard
  var latestClick = new Point(0, 0)

  val mf = new MainFrame {

    title = "DashboardApp"
    resizable = true


    contents = new Panel {

      focusable = true

      listenTo(mouse.clicks, mouse.moves, keys)

      def resetRectangular(): Unit = {
        rX = 0
        rY = 0
        rW = 0
        rH = 0
      }

      def resetCornerOffset(clicked: Chart, e: MouseEvent): Unit = {
        if (latestDraggedCorner.isDefined) {
          cornerOffset = latestDraggedCorner.get match {
            case 0 => (e.point.x - clicked.xMin, e.point.y - clicked.yMin)
            case 1 => (e.point.x - clicked.xMin, e.point.y - clicked.yMax)
            case 2 => (e.point.x - clicked.xMax, e.point.y - clicked.yMax)
            case 3 => (e.point.x - clicked.xMax, e.point.y - clicked.yMin)
          }
        }
      }

      def rectangularSelection = new Rectangle2D.Double(rX, rY, rW, rH)

      def moveChart(c: Chart, e: MouseDragged): Unit = {
        val p = new Point(e.point.x - dragPointOffset._1, e.point.y - dragPointOffset._2)
        c.moveTo(p)

      }

      def resizeChart(c: Chart, e: MouseDragged): Unit = {

        val p = new Point(e.point.x - cornerOffset._1, e.point.y - cornerOffset._2)
        c.dragCorner(p, latestDraggedCorner.get)

      }

      def snap(clicked: Chart, e: MouseDragged): Unit = {
        // A mousedragged is passed as an argument so that snap can be passed into actionIsLegal
        val closest = {
          val dummyChart: Chart = clicked.cloneChart()
          val otherCharts = db.charts.clone()
          otherCharts += dummyChart
          moveChart(dummyChart, e)
          otherCharts.filterNot(c => c.chartID == dummyChart.chartID)
            .find(c => c.masterRectangle.intersects(dummyChart.masterRectangle))
        }
        if (closest.isDefined) {
          clicked.directionTo(closest.get) match {
            case 0 => clicked.moveTo(new Point(closest.get.xMax + 1, clicked.yMin))
            case 1 => clicked.moveTo(new Point(clicked.xMin, closest.get.yMin - clicked.height - 1))
            case 2 => clicked.moveTo(new Point(closest.get.xMin - clicked.width - 1, clicked.yMin))
            case 3 => clicked.moveTo(new Point(clicked.xMin, closest.get.yMax + 1))
          }
        } else {
          // No intersecting chart was found, so collided with window
          if (clicked.xMin < 0) {
            clicked.moveTo(new Point(0, clicked.yMin))
          } else if (clicked.yMin < 0) {
            clicked.moveTo(new Point(clicked.xMin, 0))
          } else if (clicked.xMax > screenWidth) {
            clicked.moveTo(new Point(screenWidth - clicked.width - 1, clicked.yMin))
          } else if (clicked.yMax > screenWidth) {
            clicked.moveTo(new Point(clicked.xMin, screenHeight - clicked.height - 1))
          }
        }

      }

      def tryComponents(clicked: Chart, e: MouseDragged, action: (Chart, MouseDragged) => Unit): Unit = {
        /** This method is called in situations where the user's input would lead
         * to a collision. This method then divides the input into an x and a y component,
         * and tries to do the same action for both components individually. This makes
         * movement feel smoother. */

        def offset = action match {
          case moveChart => dragPointOffset
          case resizeChart => cornerOffset
        }


        var yComponent = new MouseDragged(
          e.source,
          new Point(clicked.mLocation.x + offset._1, e.point.y),
          e.modifiers)(e.peer)

        var xComponent = new MouseDragged(
          e.source,
          new Point(e.point.x, clicked.mLocation.y + offset._2),
          e.modifiers)(e.peer)

        if (wouldCollideWithChart(clicked, e, action) ||
          wouldCollideWithWindow(clicked, e, action)) {
          // This branch runs if a collision happened

          if (actionIsLegal(clicked, xComponent, action)) {
            // Collision would have happened in y, so we can still do action in x
            action(clicked, xComponent)

            if (actionIsLegal(clicked, yComponent, snap)) {
              // Snap in y direction
              snap(clicked, yComponent)
            }
            //resetCornerOffset(clicked, xComponent)
            latestClick = e.point

          }
          if (actionIsLegal(clicked, yComponent, action)) {
            // Collision would have happened in x, so we can still do action in y
            action(clicked, yComponent)

            if (actionIsLegal(clicked, xComponent, snap) && (!clicked.isInstanceOf[NumericX])) {
              // Snap in x direction
              snap(clicked, xComponent)
            }
            //resetCornerOffset(clicked, yComponent)
            latestClick = e.point

          }

          // Click chart to reset offset for natural moving
          latestClick = e.point
          dragPointOffset =
            (e.point.x - clicked.location.x, e.point.y - clicked.location.y)
          resetCornerOffset(clicked, e)

        } else {
          // No collision was detected which means that a resize made the chart too small
          if (!clicked.isInstanceOf[NumericX]) {
            // This applies to PieCharts
            if (actionIsLegal(clicked, xComponent, resizeChart)) {
              resetCornerOffset(clicked, xComponent)
              resizeChart(clicked, xComponent)

            }
            else if (actionIsLegal(clicked, yComponent, resizeChart)) {
              resetCornerOffset(clicked, yComponent)
              resizeChart(clicked, yComponent)

            }
            latestClick = e.point
          } else {
            // This applies to NumericX charts
            if (actionIsLegal(clicked, xComponent, resizeChart)) {
              resetCornerOffset(clicked, xComponent)
              resizeChart(clicked, xComponent)
              latestClick = e.point

            } else if (actionIsLegal(clicked, yComponent, resizeChart)) {
              resetCornerOffset(clicked, yComponent)
              resizeChart(clicked, yComponent)
              latestClick = e.point
            } else {
              resetCornerOffset(clicked, e)
            }

            //resetCornerOffset(clicked, e)
          }
        }
      }


      def wouldCollideWithWindow(c: Chart, e: MouseDragged, action: (Chart, MouseDragged) => Unit): Boolean = {
        val dummyChart: Chart = c.cloneChart()
        action(dummyChart, e)
        collidesWithWindow(dummyChart)
      }

      def wouldCollideWithChart(c: Chart, e: MouseDragged, action: (Chart, MouseDragged) => Unit): Boolean = {
        val dummyChart: Chart = c.cloneChart()
        action(dummyChart, e)
        collidesWithAnother(dummyChart, db.charts)
      }

      def sizeWouldBeIllegal(c: Chart, e: MouseDragged, action: (Chart, MouseDragged) => Unit): Boolean = {
        val dummyChart: Chart = c.cloneChart()
        action(dummyChart, e)
        sizeIsIllegal(dummyChart)
      }

      def sizeIsIllegal(c: Chart): Boolean = {
        c.width < c.minSize._1 || c.height < c.minSize._2
      }

      def actionIsLegal(c: Chart, e: MouseDragged, action: (Chart, MouseDragged) => Unit) = {
        val dummyChart: Chart = c.cloneChart()
        val otherCharts = db.charts.clone()
        otherCharts += dummyChart
        action(dummyChart, e)

        !(collidesWithAnother(dummyChart, otherCharts) ||
          collidesWithWindow(dummyChart) ||
          sizeIsIllegal(dummyChart))

      }

      var dragPointOffset = (0, 0)
      var cornerOffset = (0, 0)

      var latestDraggedCorner: Option[Int] = None
      var rX = 0
      var rY = 0
      var rW = 0
      var rH = 0


      reactions += {

        case e: MousePressed => {

          oChart = db.charts.find(c => c.containsPoint(e.point) ||
            c.getDraggedCorner(e.point).isDefined)
          if (oChart.isDefined && !db.highlightedCharts.contains(oChart.get)) {
            db.highlightSingleChart(oChart)
          }
          // This line highlights AND de-highlights according to click
          if (oChart.isDefined) {
            // This branch runs only if a chart was clicked
            dragPointOffset =
              (e.point.x - oChart.get.mLocation.x, e.point.y - oChart.get.mLocation.y)
            latestDraggedCorner = oChart.flatMap(_.getDraggedCorner(e.point))

            resetCornerOffset(oChart.get, e)

          }
          latestClick = e.point
          requestFocusInWindow()
          this.repaint()

          if (e.clicks == 2 && oChart.isDefined) {
            val chart = oChart.get
            creator.close()
            chart match {
              case c: LineChart => {
                creator =
                  if (c.tickerAndRange.isDefined)
                    new OnlineLineChartCreator(Some(c))
                  else
                    new LocalLineChartCreator(Some(c))
              }
              case c: BarChart => {
                creator =
                  if (c.tickerAndRange.isDefined)
                    new OnlineBarChartCreator(Some(c))
                  else
                    new LocalBarChartCreator(Some(c))
              }
              case c: PieChart => {
                creator =
                  new PieChartCreator(Some(c))
              }
            }
            creator.open()
          }

        }
        case e: MouseDragged => {

          if (oChart.isDefined) {
            // This branch runs only if a chart was clicked

            val clicked = oChart.get // Get the chart to operate on

            if (!db.highlightedCharts.contains(clicked)) {
              db.highlightSingleChart(oChart)
            }

            if (latestDraggedCorner.isDefined) {
              // Border was dragged
              if (actionIsLegal(clicked, e, resizeChart)) {

                // Resize is legal: resize chart (if no collision)
                resizeChart(clicked, e)

              } else {
                // Resize is illegal

                tryComponents(clicked, e, resizeChart)

              }

            } else {
              // Chart wasn't dragged by its border

              if (actionIsLegal(clicked, e, moveChart)) {
                // Move is legal: move al highlighted charts
                moveChart(clicked, e)

              } else {
                // Move is illegal

                tryComponents(clicked, e, moveChart)

              }
            }


          } else {
            // No chart was clicked, so create rectangular selection

            // Process x
            if (latestClick.x < e.point.x) {
              rX = latestClick.x
              rW = e.point.x - rX
            } else {
              rX = e.point.x
              rW = latestClick.x - e.point.x
            }
            // Process y
            if (latestClick.y < e.point.y) {
              rY = latestClick.y
              rH = e.point.y - rY
            } else {
              rY = e.point.y
              rH = latestClick.y - e.point.y
            }
          }

          requestFocusInWindow()
          this.repaint()

        }
        case e: MouseReleased => {

          // Highlight charts that are within selection
          db.highlightMultipleCharts(db.charts.filter(c => {
            rectangularSelection.contains(c.masterRectangle) ||
              c.masterRectangle.contains(latestClick)
          }))
          resetRectangular()
          repaint()

        }
        case e: KeyPressed =>
          if (e.peer.getKeyCode == 8) {
            // Backspace was pressed
            db.deleteHighlightedChart()
          }
          if (e.peer.getKeyCode == java.awt.event.KeyEvent.VK_R) {
            db.charts.foreach(_.updateData())
          }
          this.repaint
        case _: FocusLost => repaint()
      }

      override def paintChildren(g: Graphics2D): Unit = {

        // Set anti-aliasing
        g.setRenderingHint(
          RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        for (component <- contents) {
          component.paint(g)
        }

        // Draw charts
        for (chart <- db.charts) {
          g.translate(chart.mLocation.x, chart.mLocation.y)
          g.setFont(new Font("Arial", Font.PLAIN, 14))
          chart.paintChart(g)
          g.translate(-chart.mLocation.x, -chart.mLocation.y)
        }

        // Draw rectangular selection
        g.setColor(Color.BLACK)
        val dashed = new BasicStroke(
          1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, Array[Float](5), 0)
        g.setStroke(dashed)
        g.setColor(Color.GRAY)
        g.draw(rectangularSelection)

        // Draw statistical info
        if (oChart.isDefined) {
          val chart = oChart.get
          chart match {
            case c: NumericX => {
              val average = (c.nd.parsedData.head.map(_._2)).sum / (c.nd.parsedData.head).length
              val statString = s"min:   ${c.nd.minYOfAllSeries}  |  " +
                s"max:   ${c.nd.maxYOfAllSeries}  |  " +
                s"avg:   ${average}  |  " +
                s"sum:   ${(c.nd.parsedData.head.map(_._2)).sum}  |  " +
                s"std:   ${c.nd.parsedData.head.map( l => math.abs(average - l._2)).sum / (c.nd.parsedData.head).length}"
              g.drawString(statString, 20, screenHeight - 80)
            }
            case _ => {}
          }
        }

        // Draw data-point info

      }

    }

    size = new Dimension(1000, 1000)
    this.maximize()

  }
  val saveChooser = new FileChooser
  val openChooser = new FileChooser

  val saveMenuItem = new MenuItem(Action("Save as") {
    saveChooser.fileFilter = new FileNameExtensionFilter("JSON file", "json")
    saveChooser.showSaveDialog(mf)
    val attempt = Try({
      var path = saveChooser.peer.getSelectedFile.toString
      if (!path.endsWith(".json")) path += ".json"
      val newSave = path
      val writer = new FileWriter(newSave)
      writer.write(SaveFileManager.chartsAsJson(db.charts.toList).toString())
      writer.close()
    })
  })

  val openMenuItem = new MenuItem(Action("Open") {
    openChooser.fileFilter = new FileNameExtensionFilter("JSON file", "json")
    openChooser.showOpenDialog(mf)
    val attempt = Try({
      var file: File = openChooser.selectedFile
      val source = scala.io.Source.fromFile(file)
      val jsonString: Json = parse(source.mkString).getOrElse(Json.Null)
      db.charts = SaveFileManager.jsonToCharts(jsonString).toBuffer
      mf.repaint()
      source.close()
    })
    attempt match {
      case Failure(exception) => {
        var errorText = ""
        exception match {
          case e: NullPointerException => errorText = "java.lang.exception: IllegalSheetException"
          case _ => {
            errorText = exception.toString
            val errorPopup = new Dialog {
              contents = new FlowPanel {
                contents += new Label(errorText)
              }
              centerOnScreen()
            }
            errorPopup.open()
          }
        }

      }
      case Success(value) => {}
    }
  })

  val fileMenu = new Menu("File") {
    contents += saveMenuItem
    contents += openMenuItem
  }

  val localMenu = new Menu("Local") {
    contents += new MenuItem(Action("Line chart") {
      creator.close()
      creator = new LocalLineChartCreator(None)
      creator.open()
    })
    contents += new MenuItem(Action("Bar chart") {
      creator.close()
      creator = new LocalBarChartCreator(None)
      creator.open()
    })
    contents += new MenuItem(Action("Pie chart") {
      creator.close()
      creator = new PieChartCreator(None)
      creator.open()
    })
  }

  val onlineMenu = new Menu("Financial") {
    contents += new MenuItem(Action("Line chart") {
      creator.close()
      creator = new OnlineLineChartCreator(None)
      creator.open()
    })
    contents += new MenuItem(Action("Bar chart") {
      creator.close()
      creator = new OnlineBarChartCreator(None)
      creator.open()
    })

  }
  val creationMenu = new Menu("Create chart") {
    contents += localMenu
    contents += onlineMenu
  }
  val mBar = new MenuBar() {
    contents += fileMenu
    contents += creationMenu
    contents += new MenuItem(Action("Help") {
      val helper = new Dialog {
        contents = new BoxPanel(Orientation.Vertical) {
          border = EmptyBorder(10, 10, 10, 10)
          contents += new Label("Welcome.")
          contents += new Label(
            "You can visualize local or financial " +
              "data by clicking the \"Create chart\" button.")
          contents += new Label(
            "Local data should be stored in .xlsx files (default output in Excel).")
          contents += new Label(
            "Data should be stored in (arbitrarily many) adjacent columns, of which the leftmost stores X values.")
          contents += new Label(
            "Pie charts should have string values on the X axis, other charts should have numeric values.")
          contents += new Label(
            "You can survey financial data by providing an asset's ticker and a timeframe.")
          contents += new Label(
            "You can edit a chart by double clicking it and delete it by pressing backspace. Refresh all data by pressing R.")
          contents += new Label(
            "Save your session or continue a previous one from the File menu.")

          centerOnScreen()
        }
      }
      helper.open()
    })
  }

  var oChart: Option[Chart] = None // Tracks which chart is clicked
  var creator: ChartCreator = new LocalLineChartCreator(None)
  var screenWidth = 0
  var screenHeight = 0
  var popUpIsVisible = false

  //mf.listenTo(creator.createButton)

  mf.menuBar = mBar

  def collidesWithAnother(c: Chart, otherCharts: Buffer[Chart]): Boolean = {
    otherCharts.filterNot(_.chartID == c.chartID)
      .exists(another => {
        another.corners.exists(corner => c.containsPoint(corner)) ||
          c.corners.exists(corner => another.containsPoint(corner))
      })
  }

  def collidesWithWindow(c: Chart): Boolean = {
    screenWidth = math.max(screenWidth, mf.size.width)
    screenHeight = math.max(screenHeight, mf.size.height)
    c.corners(0).x < 0 ||
      c.corners(1).y > screenHeight ||
      c.corners(2).x > screenWidth ||
      c.corners(3).y < 0

  }

  UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName)

  def top = mf

}
