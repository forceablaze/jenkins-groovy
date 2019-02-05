import hudson.Util;
import java.time.Duration
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.LocalDateTime
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Calendar
import groovy.transform.Sortable
import groovy.transform.ToString

def outputMap = [:]
def slaveBuildHistory = [:]
def slaveTimetable = [:]
def List<Thread> threads = []
def DURATION

def getRate = { x ->
  return Math.round(x * 10000) / 10000
}

def putJSONText = { sb, String... texts ->
  sb << 'JSONSTRING:'
  for( String s : texts)
    sb << s
}

@Sortable
class TimePeriod {
  long start
  long duration

  def getEnd() {
    return this.start + this.duration
  }

  def isOverlap(TimePeriod period) {
    if(period.start <= this.start + this.duration)
	  return true
	if(period.end >= this.start)
	  return true
	return false
  }

  def merge(TimePeriod period) {

    if(!isOverlap(period))
      return this

    def newStart = 0
    if(period.start < this.start)
      newStart = period.start
    else
      newStart = this.start

    def newEnd = 0
    if(period.end > this.end)
      newEnd = period.end
    else
      newEnd = this.end

    return new TimePeriod(newStart, newEnd)
  }
}

@Sortable(includes = ['time'])
@ToString
class TimeFlag {
  long time
  def isStartTime
}

def formatDuration = { long duration ->
  return Util.getTimeSpanString(duration);
}

def formatDateString(LocalDateTime time) {
  DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
  return time.format(formatter)
}

def getCurrentZonedTime() {
  def localTime = ZonedDateTime.now() 
  return localTime
}

def toEpochMillis(LocalDateTime time) {
  return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

def getInstanceInfo = { Slave slave, fromDateEpoch, toDateEpoch, isLast ->

  def slaveName = slave.nodeName

  def sb = ''<<''
  outputMap << [(slaveName):sb]


  def allBuilds = slave.computer.builds
  slaveBuildHistory << [(slaveName):allBuilds]

  def timeline = new ArrayList<TimeFlag>()
  slaveTimetable << [(slaveName):timeline]

  //sb <<'Slave Name:' + slaveName + '\n'
  putJSONText(sb, '    \"' + slaveName + '\":{\n')

  def t = Thread.start {
    def strBuff = outputMap[slaveName]
    def computer = slave.getComputer()
    def builds = slaveBuildHistory[slaveName]

    def line = slaveTimetable[slaveName]
    def iter = builds.iterator()
    putJSONText(strBuff, '      \"builds\":{\n')
    // put each build timestamp to list

    def isFirst = true
	def build_count = 0
    builds.each {

      def start = it.getStartTimeInMillis()

      def duration = 0
	  if(it.duration == 0)
        duration = toEpochMillis(getCurrentZonedTime().toLocalDateTime()) - start
      else
        duration = it.duration

      def end = start + duration

      if(start < fromDateEpoch)
        return
      if(end > toDateEpoch)
        end = toDateEpoch

      if(isFirst)
        isFirst = false
      else
        putJSONText(strBuff, '        ,\n')

      build_count++

      putJSONText(strBuff, '        \"', it.parent.name, '\":{\n')
      putJSONText(strBuff, '          \"number\":', it.number.toString(), ',\n')
      putJSONText(strBuff, '          \"start\":', start.toString(), ',\n')
      putJSONText(strBuff, '          \"duration\":', duration.toString(), ',\n')
      putJSONText(strBuff, '          \"duration_string\":\"', formatDuration(duration), '\",\n')
      putJSONText(strBuff, '          \"end\":', end.toString(), '\n')

      line << new TimeFlag(time: start, isStartTime: true)
      line << new TimeFlag(time: end, isStartTime: false)

      putJSONText(strBuff, '        }\n')
	}
    putJSONText(strBuff, '      },\n')

    def sorted_timeline = line.sort(false)
	def newTimePeriod = new ArrayList<TimePeriod>()

    if(build_count != 0) {
      long firstBusyStartTime = sorted_timeline.get(0).time
      long lastBusyEndTime = sorted_timeline.get(sorted_timeline.size() - 1).time
      putJSONText(strBuff, '      \"first_busy_start_time\":', firstBusyStartTime.toString(), ',\n')
      putJSONText(strBuff, '      \"last_busy_end_time\":', lastBusyEndTime.toString(), ',\n')
    }

    def depthCount = 0
	def switchOn = true

    // the start time of busy time period
	def startTime = 0
    for(def idx = 0; idx < sorted_timeline.size(); idx++) {
      def item = sorted_timeline[idx]
      //strBuff <<'timetable:'<< item << ':' << depthCount << ':' << switchOn << '\n'

      if(item.isStartTime) {

        // meet a new time period
        if(switchOn)
          startTime = item.time

        if(depthCount == 0)
          switchOn = false
        depthCount += 1
      }
      else {
        depthCount -= 1

		if(depthCount == 0)
          switchOn = true

        // meet the end time of current busy time
        if(switchOn)
          newTimePeriod << new TimePeriod(start: startTime, duration: item.time - startTime)
      }
	}
    //strBuff << 'checked busy time period count:' << newTimePeriod.size() << '\n'

    def totalBusyTime = 0
    newTimePeriod.each {
      totalBusyTime += it.duration
    }

    //strBuff << slaveName << ':totalBusyTime:' << totalBusyTime << ':' << formatDuration(totalBusyTime) << '\n'
    putJSONText(strBuff, '      \"build_count\":', build_count.toString(), ',\n')
    putJSONText(strBuff, '      \"busy_time_period_count\":', newTimePeriod.size().toString(), ',\n')
    putJSONText(strBuff, '      \"total_busy_time\":', totalBusyTime.toString(), ',\n')
    putJSONText(strBuff, '      \"total_busy_time_string\":\"', formatDuration(totalBusyTime), '\",\n')
    putJSONText(strBuff, '      \"busy_rate\":\"', getRate(totalBusyTime/DURATION).toString(), '\"\n')

    if(!isLast)
      putJSONText(strBuff, '    },')
    else
      putJSONText(strBuff, '    }')
  }
  threads << t
}

def run = { fromDateString = null, toDateString = null ->

  DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  def zonedTime = getCurrentZonedTime()
  def currentDate = zonedTime.toLocalDateTime()
  if(toDateString == null)
    toDateString = formatDateString(currentDate)

  def toDate = LocalDateTime.parse(toDateString, formatter)

  if(fromDateString == null)
      fromDateString = formatDateString(toDate.minusDays(7))
  def fromDate = LocalDateTime.parse(fromDateString, formatter)

  def fromDateEpoch = toEpochMillis(fromDate)
  def toDateEpoch = toEpochMillis(toDate)
  DURATION = toDateEpoch - fromDateEpoch

  def jsonSB = ''<<''
  putJSONText(jsonSB, '{\n')
  putJSONText(jsonSB, '  \"from_date\":\"' + fromDateString + '\",\n')
  putJSONText(jsonSB, '  \"to_date\":\"' + toDateString + '\",\n')
  putJSONText(jsonSB, '  \"duration\":\"' + DURATION.toString() + '\",\n')
  putJSONText(jsonSB, '  \"duration_string\":\"' + formatDuration(DURATION) + '\",\n')
  putJSONText(jsonSB, '  \"computers\":{')
  println jsonSB
  jsonSB.length = 0


  def slaves = Hudson.instance.slaves
  for(def i = 0; i < slaves.size; i++) {
    def it = slaves[i]
    getInstanceInfo(it, fromDateEpoch, toDateEpoch, i + 1 == slaves.size)
  }

  threads.each { t ->
    t.join()
  }

  outputMap.each { k, v ->
    println v
  }

  putJSONText(jsonSB, '  }\n')
  putJSONText(jsonSB, '}')
  println jsonSB
}
