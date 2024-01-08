package charts

import io.circe.{HCursor, Json}

import scala.io.Source
import io.circe.parser._
import scala.language.postfixOps


object YahooFinanceParser {

  val ranges = Array(
    "1d",
    "5d",
    "1mo",
    "3mo",
    "6mo",
    "1y",
    "2y",
    "5y",
    "10y",
    "ytd",
    "max"
  )

  val intervalsByRange = Map(
    "1d" -> "5m",
    "5d" -> "30m",
    "1mo" -> "1d",
    "3mo" -> "1d",
    "6mo" -> "1d",
    "1y" -> "1d",
    "2y" -> "1wk",
    "5y" -> "1mo",
    "10y" -> "1mo",
    "ytd" -> "1d",
    "max" -> "3mo"
  )

  def getURL(ticker: String, range: String) = {
    s"https://query1.finance.yahoo.com/v8/finance/chart/$ticker?interval=${intervalsByRange(range)}&range=$range"
  }

  def downloadJson(ticker: String, range: String) = {
    val url = s"https://query1.finance.yahoo.com/v8/finance/chart/$ticker?interval=${intervalsByRange(range)}&range=$range"
    val json = Source.fromURL(url)
    json.mkString
  }

  def closingPrices(url: String): Array[(Double, Double)] = {
    val source = Source.fromURL(url)
    val doc: Json = parse(source.mkString).getOrElse(Json.Null)
    val cursor: HCursor = doc.hcursor
    val timestamps = cursor
      .downField("chart")
      .downField("result")
      .downArray
      .get[Array[Int]]("timestamp")
      .toTry
      .get
    val closings = cursor
      .downField("chart")
      .downField("result")
      .downArray
      .downField("indicators")
      .downField("quote")
      .downArray
      .get[Array[Double]]("close")
      .toTry
      .get
    timestamps.map(_.toDouble) zip closings
  }

}
