package charts

import java.lang.Math.round
import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.math.{abs, floor, log10, pow}

class NumericXData(
                    titleOptions: Array[Option[String]],
                    fp: Option[String],
                    val tickerAndRange: Option[(String, String)],
                    sheet: Int,
                    val xRange: (Option[Double], Option[Double]),
                  ) {

  val parsedData: Array[Array[(Double, Double)]] = {

    if (fp.isDefined) {
      if (tickerAndRange.isDefined) {
        Array(YahooFinanceParser.closingPrices(fp.get).filter(pair => pair._2.toDouble == pair._2))
      } else {
        SheetParser.parseNumericData(fp.get, sheet, xRange)
      }
    }
    else
      Array[Array[(Double, Double)]]()
  }
  val seriesX: Array[Double] = parsedData.headOption.getOrElse(Array[(Double, Double)]()).map(_._1)
  val maxYOfAllSeries: Double = parsedData.flatten.map(_._2).maxOption.getOrElse(0)
  val minYOfAllSeries: Double = parsedData.flatten.map(_._2).minOption.getOrElse(0)
  val maxXOfAllSeries: Double = parsedData.flatten.map(_._1).maxOption.getOrElse(0)
  val minXOfAllSeries: Double = parsedData.flatten.map(_._1).minOption.getOrElse(0)
  val xAxisRange: Double = maxXOfAllSeries - minXOfAllSeries
  val yAxisRange: Double = maxYOfAllSeries - minYOfAllSeries
  /** The following keys are used: mainTitle, xAxisTitle, yAxisTitle */
  val powerOfTenY = getPowerOf10(yAxisRange)
  val powerOfTenX = getPowerOf10(xAxisRange)
  val gapsByFirstDigit = Map[Double, Double](
    0.0 -> 0.1,
    1.0 -> 0.2,
    2.0 -> 0.5,
    3.0 -> 0.5,
    4.0 -> 0.5,
    5.0 -> 1,
    6.0 -> 1,
    7.0 -> 1,
    8.0 -> 1,
    9.0 -> 1
  )
  val gapBetweenHorLines: Double = {
    gapsByFirstDigit(floor((yAxisRange / pow(10, powerOfTenY)))) * pow(10, powerOfTenY)
  }
  val firstHorLine: Double = {
    val base = findClosestLines(gapBetweenHorLines, minYOfAllSeries) //- gapBetweenHorLines
    if (base >= minYOfAllSeries)
      base - gapBetweenHorLines
    else
      base
  }
  val lastHorLine: Double = {
    val base = findClosestLines(gapBetweenHorLines, maxYOfAllSeries) //- gapBetweenHorLines
    if (base <= maxYOfAllSeries)
      base + gapBetweenHorLines
    else
      base
  }
  val gapBetweenVerLines: Double = {
    gapsByFirstDigit(floor((xAxisRange / pow(10, powerOfTenX)))) * pow(10, powerOfTenX)
  }
  val firstVerLine: Double = {
    val base = findClosestLines(gapBetweenVerLines, minXOfAllSeries) //- gapBetweenHorLines
    if (base > minXOfAllSeries)
      base - gapBetweenVerLines
    else
      base
  }
  var titles = Map[String, String]()
  if (titleOptions.head.isDefined) titles = titles + ("mainTitle" -> titleOptions.head.get)
  if (titleOptions(1).isDefined) titles = titles + ("xAxisTitle" -> titleOptions(1).get)
  if (titleOptions(2).isDefined) titles = titles + ("yAxisTitle" -> titleOptions(2).get)
  val lastVerLine: Double = {
    val base = findClosestLines(gapBetweenVerLines, maxXOfAllSeries) //- gapBetweenHorLines
    if (base < maxXOfAllSeries)
      base + gapBetweenVerLines
    else
      base
  }
  val horLines = {
    divideRange((firstHorLine, lastHorLine), gapBetweenHorLines)
  }

  val verLines = {
    if (tickerAndRange.isEmpty) {
      divideRange((firstVerLine, lastVerLine), gapBetweenVerLines)
    } else {

      val intervals: Map[String, String] = YahooFinanceParser.intervalsByRange
      val interval = intervals(tickerAndRange.get._2)

      val firstTime: LocalDateTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(minXOfAllSeries.toLong * 1000),
        ZoneOffset.ofHours(2)
      )
      val firstInstant = Instant.ofEpochMilli(minXOfAllSeries.toLong * 1000)
      val lastTime: LocalDateTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(maxXOfAllSeries.toLong * 1000),
        ZoneOffset.ofHours(2)
      )
      val lastInstant = Instant.ofEpochMilli(maxXOfAllSeries.toLong * 1000)

      var times = Array[Double]()

      var timeCursor = firstTime.plusSeconds(0)
      // Set which times to show on vertical lines

      def addTimesHourly: Unit = {
          for (m <- 0 to 21 by 3) {
            val seconds = firstTime
              .withHour(m)
              .withMinute(0)
              .toInstant(ZoneOffset.ofHours(2))
              .toEpochMilli / 1000
            if (minXOfAllSeries <= seconds && seconds <= maxXOfAllSeries) {
              times = times :+ seconds.toDouble
            }
          }
        }

      def addTimesDaily(dayRange: Range): Unit = {
        for (d <- dayRange) {
          val seconds = firstTime
            .plusDays(d)
            .withHour(0)
            .withMinute(0)
            .toInstant(ZoneOffset.ofHours(2))
            .toEpochMilli / 1000
          if (minXOfAllSeries <= seconds && seconds <= maxXOfAllSeries) {
            times = times :+ seconds.toDouble
          }
        }
      }

      def addTimesMonthly(monthRange: Range, days: Array[Int]): Unit = {
        for (m <- monthRange; d <- days) {
          val seconds = firstTime
            .plusMonths(m)
            .withDayOfMonth(d)
            .withHour(0)
            .withMinute(0)
            .toInstant(ZoneOffset.ofHours(2))
            .toEpochMilli / 1000
          if (minXOfAllSeries <= seconds && seconds <= maxXOfAllSeries) {
            times = times :+ seconds.toDouble
          }
        }
      }

      def addTimesYearly(yearRange: Range, months: Array[Int]): Unit = {
        for (y <- yearRange; m <- months) {
          val seconds = firstTime
            .plusYears(y)
            .withMonth(m)
            .withDayOfMonth(1)
            .withHour(0)
            .withMinute(0)
            .toInstant(ZoneOffset.ofHours(2))
            .toEpochMilli / 1000
          if (minXOfAllSeries <= seconds && seconds <= maxXOfAllSeries) {
            times = times :+ seconds.toDouble
          }
        }
      }

      tickerAndRange.get._2 match {

        case "1d" => addTimesHourly
        case "5d" => addTimesDaily((0 to 7))
        case "1mo" => addTimesMonthly((0 to 1), Array(1, 11, 21))
        case "3mo" => addTimesMonthly((0 to 3), Array(1, 16))
        case "6mo" | "ytd" => addTimesMonthly((0 to 6), Array(1))
        case "1y" => addTimesMonthly((0 to 12 by 3), Array(1))
        case "2y" => addTimesYearly((0 to 2), Array(1, 7))
        case "5y" => addTimesYearly((0 to 5), Array(1))
        case "10y" => addTimesYearly((0 to 10 by 2), Array(1))
        case "max" => addTimesYearly((0 to 100 by 4), Array(1))
      }

      times

    }
  }
  //if (tickerAndRange.isDefined) { titles = titles + (("mainTitle", tickerAndRange.get._1.toUpperCase)) }

  def findClosestLines(gap: Double, t: Double): Double = {
    val E = pow(10, getPowerOf10(t))
    val normT = abs(t / E * 10)
    val normG = gap / E * 10
    val test1 = floor(normT)
    if (normG.toInt == 0) {
      return t
    }
    val absolute =
      round(floor(normT).toInt - floor(normT).toInt % normG.toInt) *
        E / 10
    if (t < 0) -absolute else absolute
  }

  def getPowerOf10(t: Double) = if (t == 0) 0 else floor(log10(abs(t)))

  def divideRange(range: (Double, Double), gap: Double): Array[Double] = {
    val n = (range._2 - range._1) / gap
    val base = {
      (for (t <- 0 to n.toInt) yield {
        range._1 + gap * t
      }).toArray
    }
    if (!base.contains(range._2))
      base// :+ range._2
    else
      base
  }

  // The following methods help with rendering
}
