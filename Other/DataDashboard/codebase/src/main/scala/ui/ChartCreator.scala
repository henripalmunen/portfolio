package ui

import scala.swing._
import charts._

import java.awt.Color
import javax.swing.UIManager
import javax.swing.border.Border
import scala.util.{Failure, Success, Try}
import ui.{Dashboard => db}

import java.io.File
import scala.swing.Swing.EmptyBorder

abstract class ChartCreator(oChart: Option[Chart]) extends Dialog(DashboardApp.mf) {

  var oCreatedChart: Option[Chart]
  var components: Array[Array[Option[Component]]]

  def masterButton(isEditing: Boolean) = new Button(Action(

    if (isEditing) "Edit" else if (oChart.isDefined) "Copy & Paste" else "Create") {
    if (isEditing) db.charts -= oChart.get
    val attempt = tryToCreate()

    attempt match {
      case Failure(exception) => {
        var errorText = ""
        exception match {
          case e: NullPointerException => errorText = "java.lang.exception: IllegalSheetException"
          case _ => errorText = exception.toString
        }
        val errorPopup = new Dialog {
          contents = new FlowPanel {
            contents += new Label(errorText)
          }
          centerOnScreen()
        }
        errorPopup.open()
      }
      case Success(value) => {}
    }

  })

  def loc: Point = if (oChart.isDefined) oChart.get.mLocation else new Point(0, 0)

  def tryToCreate(): Try[Unit]

  def setDefaults: Unit

  def createIfFits() = {
    val chart = oCreatedChart.get
    for (i <- (0 to 8); j <- (0 to 8)) {
      if (DashboardApp.collidesWithAnother(chart, db.charts) ||
        DashboardApp.collidesWithWindow(chart)) {
        chart.moveTo(new Point(((DashboardApp.mf.size.width - (720 * 0.87)) / 8 * i).toInt,
          ((DashboardApp.mf.size.height - (490 * 0.87)) / 8 * j).toInt))
      }
    }
    if (!(DashboardApp.collidesWithAnother(chart, db.charts) ||
      DashboardApp.collidesWithWindow(chart))) {
      db.createChart(oCreatedChart.get)
    } else {
      throw new Exception("No more space in current dashboard")
    }
    this.close()
    DashboardApp.mf.repaint()
  }

  def updateContents() = {

    this.contents = new GridBagPanel {
      val c: Constraints = new Constraints()
      c.gridy = 0
      for (row <- components) {
        c.fill = GridBagPanel.Fill.Horizontal
        c.gridx = 0
        c.weightx = 0.2
        for (component <- row.take(2)) {
          if (component.isDefined) {
            layout(component.get) = c
          }
          c.gridx = c.gridx + 1
        }
        c.weightx = 0.6
        if (row.last.isDefined) {
          layout(row.last.get) = c
        }
        c.gridy = c.gridy + 1
      }
    }
  }
}

abstract class LocalChartCreator(oChart: Option[Chart]) extends ChartCreator(oChart) {

  resizable = false
  preferredSize = new Dimension(600, 240)

  val isEditing = oChart.isDefined
  val chooser = new FileChooser
  val fpLabel = new Label("")
  val sheetLabel = new Label("Sheet index") {
    border = EmptyBorder(0, 20, 0, 0)
    xAlignment = Alignment.Left
  }
  val sheetField = new TextField("") {
    text = " "
  }
  val chooseButton = new Button(Action("Choose file") {
    chooser.showOpenDialog(this)
    Try({
      fpLabel.text = chooser.selectedFile.getName
    })
  })
  val textOptional = new Label("Optional:") {
    border = EmptyBorder(20, 20, 0, 0)
    xAlignment = Alignment.Left
    yAlignment = Alignment.Bottom
  }
  val mainTitleLabel = new Label("Chart title") {
    border = EmptyBorder(0, 20, 0, 0)
    xAlignment = Alignment.Left
  }
  val mainTitleField = new TextField()
  val xTitleLabel = new Label("X axis title") {
    border = EmptyBorder(0, 20, 0, 0)
    xAlignment = Alignment.Left
  }
  val xTitleField = new TextField()
  val yTitleLabel = new Label("Y axis title") {
    border = EmptyBorder(0, 20, 0, 0)
    xAlignment = Alignment.Left
  }
  val yTitleField = new TextField()
  val validRanges = YahooFinanceParser.ranges
  val combo = new ComboBox(validRanges) {
    preferredSize = chooseButton.preferredSize
  }
  var components: Array[Array[Option[Component]]] = Array(
    Array(None, Some(chooseButton), Some(fpLabel)),
    Array(Some(sheetLabel), Some(sheetField), None),
    Array(Some(textOptional), None, None),
    Array(Some(mainTitleLabel), Some(mainTitleField), None),
  )
  listenTo(combo.selection)
  var oCreatedChart: Option[Chart] = None

  this.contents = new GridBagPanel
  updateContents()
  centerOnScreen()

  def setDefaults() = {
    require(oChart.isDefined)
    val c = oChart.get
    oCreatedChart = Some(c)
    fpLabel.text = new File(oChart.get.mFilePath.get).getName
    mainTitleField.text = c.titles.getOrElse("mainTitle", "")
  }

}

class PieChartCreator(oChart: Option[PieChart]) extends LocalChartCreator(oChart) {

  title = "Pie chart  |  Local"

  components = components :+ Array(None, None, Some(masterButton(oChart.isDefined)))
  if (oChart.isDefined) {
    components = components :+ Array(None, None, Some(masterButton(false)))
  }
  updateContents()


  def tryToCreate() = Try({

    oCreatedChart = Some(new PieChart(
      if (mainTitleField.text.nonEmpty) Some(mainTitleField.text) else None,
      loc,
      Some(chooser.selectedFile.getAbsolutePath),
      try {
        db.charts.last.chartID + 1
      } catch {
        case e: IndexOutOfBoundsException => 0
      },
      sheetField.text.trim.toInt))

    createIfFits()
  })

  override def setDefaults(): Unit = {
    super.setDefaults()
    val c = oChart.get
    chooser.selectedFile = new File(c.mFilePath.get)
    sheetField.text = c.sheet.toString
    mainTitleField.text = c.titles.getOrElse("mainTitle", "")
  }

  if (oChart.isDefined) setDefaults()
}

class LocalLineChartCreator(oChart: Option[NumericX]) extends LocalChartCreator(oChart) {

  title = "Line chart  |  Local"

  val minXLabel = new Label("X lower limit") {
    border = EmptyBorder(0, 20, 0, 0)
    xAlignment = Alignment.Left
  }
  val minXField = new TextField()
  val maxXLabel = new Label("X higher limit") {
    border = EmptyBorder(0, 20, 0, 0)
    xAlignment = Alignment.Left
  }
  val maxXField = new TextField()

  override def setDefaults(): Unit = {
    super.setDefaults()
    val c = oChart.get
    chooser.selectedFile = new File(c.mFilePath.get)
    sheetField.text = c.sheet.toString
    xTitleField.text = c.titles.getOrElse("xAxisTitle", "")
    yTitleField.text = c.titles.getOrElse("yAxisTitle", "")
    minXField.text = c.xRange._1.map(_.toString).getOrElse("")
    maxXField.text = c.xRange._2.map(_.toString).getOrElse("")
  }


  components = components :+ Array(Some(xTitleLabel), Some(xTitleField), None)
  components = components :+ Array(Some(yTitleLabel), Some(yTitleField), None)
  components = components :+ Array(Some(minXLabel), Some(minXField), None)
  components = components :+ Array(Some(maxXLabel), Some(maxXField), None)
  components = components :+ Array(None, None, Some(masterButton(oChart.isDefined)))
  if (oChart.isDefined) {
    components = components :+ Array(None, None, Some(masterButton(false)))
  }
  updateContents()

  def tryToCreate() = Try({
    val titleOptions = Array(mainTitleField, xTitleField, yTitleField)
      .map(f => if (f.text.nonEmpty) Some(f.text) else None)

    oCreatedChart = Some(new LineChart(
      titleOptions,
      loc,
      Some(chooser.selectedFile.getAbsolutePath),
      None,
      try {
        db.charts.last.chartID + 1
      } catch {
        case e: IndexOutOfBoundsException => 0
      },
      sheetField.text.trim.toInt,
      aXRange
    ))
    createIfFits()

  })

  def aXRange = {
    if (minXField.text.nonEmpty) {
      val thrower = minXField.text.toDouble
    }
    if (maxXField.text.nonEmpty) {
      val thrower = maxXField.text.toDouble
    }
    (minXField.text.toDoubleOption,
      maxXField.text.toDoubleOption)
  }

  if (oChart.isDefined) setDefaults()
}

class LocalBarChartCreator(oChart: Option[BarChart])
  extends LocalLineChartCreator(oChart) {

  title = "Bar chart  |  Local"

  override def tryToCreate() = Try({
    val titleOptions = Array(mainTitleField, xTitleField, yTitleField)
      .map(f => if (f.text.nonEmpty) Some(f.text) else None)
    oCreatedChart = Some(new BarChart(
      titleOptions,
      loc,
      Some(chooser.selectedFile.getAbsolutePath),
      None,
      try {
        db.charts.last.chartID + 1
      } catch {
        case e: IndexOutOfBoundsException => 0
      },
      sheetField.text.trim.toInt,
      aXRange
    ))
    createIfFits()
  })

  if (oChart.isDefined) setDefaults()

}

abstract class OnlineChartCreator(oChart: Option[NumericX]) extends ChartCreator(oChart) {

  resizable = false
  preferredSize = new Dimension(400, 140)

  val isEditing = oChart.isDefined
  val tickerLabel = new Label("Ticker (international)") {
    border = EmptyBorder(0, 20, 0, 0)
    xAlignment = Alignment.Left
  }
  val tickerField = new TextField()
  val validRanges = YahooFinanceParser.ranges
  val rangeLabel = new Label("Range") {
    border = EmptyBorder(0, 20, 0, 0)
    xAlignment = Alignment.Left
  }
  val rangeCombo = new ComboBox(validRanges)
  var oCreatedChart: Option[Chart] = None
  var components: Array[Array[Option[Component]]] = Array(
    Array(Some(tickerLabel), Some(tickerField), None),
    Array(Some(rangeLabel), Some(rangeCombo), None)
  )

  def titleOptions = Array(Some(ticker), None, Some("USD"))

  def ticker = tickerField.text.toUpperCase

  def range = rangeCombo.selection.item

  listenTo(rangeCombo.selection)

  centerOnScreen()

  def setDefaults(): Unit = {
    require(oChart.isDefined && oChart.get.tickerAndRange.isDefined)
    val c = oChart.get
    tickerField.text = c.tickerAndRange.get._1
    rangeCombo.selection.item = c.tickerAndRange.get._2
  }

}

class OnlineLineChartCreator(oChart: Option[NumericX])
  extends OnlineChartCreator(oChart) {

  title = "Line chart  |  Financial"

  components = components :+ Array(None, None, Some(masterButton(oChart.isDefined)))
  if (oChart.isDefined) {
    components = components :+ Array(None, None, Some(masterButton(false)))
  }
  updateContents()

  def tryToCreate() = Try({
    oCreatedChart = Some(new LineChart(
      titleOptions,
      loc,
      Some(YahooFinanceParser.getURL(ticker, range)),
      Some((ticker, range)),
      try {
        db.charts.last.chartID + 1
      } catch {
        case e: IndexOutOfBoundsException => 0
      },
      0,
      (None, None)
    ))
    createIfFits()
  })

  if (oChart.isDefined) setDefaults()

}

class OnlineBarChartCreator(oChart: Option[BarChart])
  extends OnlineLineChartCreator(oChart) {

  title = "Bar chart  |  Financial"

  override def tryToCreate() = Try({
    oCreatedChart = Some(new BarChart(
      titleOptions,
      loc,
      Some(YahooFinanceParser.getURL(ticker, range)),
      Some((ticker, range)),
      try {
        db.charts.last.chartID + 1
      } catch {
        case e: IndexOutOfBoundsException => 0
      },
      -1,
      (None, None)
    ))

    createIfFits()

  })

  if (oChart.isDefined) setDefaults()

}


object tester extends SimpleSwingApplication {

  UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName)

  def top = new MainFrame {

    centerOnScreen()

    val cc = new OnlineLineChartCreator(None)

    contents = new BoxPanel(Orientation.Vertical) {
      contents += new Button(Action("Create Chart") {
        cc.open()
      })
    }
  }

}