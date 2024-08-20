package challenge.utils

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId, ZoneOffset}


object DateTimeUtils {

  def toUtc(localDateTime: LocalDateTime): LocalDateTime =
    localDateTime
      .atZone(ZoneId.systemDefault())
      .withZoneSameInstant(ZoneOffset.UTC)
      .toLocalDateTime

  def formatDateTime(dateTime: LocalDateTime): String =
    dateTime.format(DateTimeFormatter.ISO_DATE_TIME)
}
