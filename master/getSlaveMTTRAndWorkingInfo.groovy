import hudson.Util;
import java.time.Duration
import java.time.ZonedDateTime
import java.text.SimpleDateFormat
import java.util.Calendar
import groovy.transform.Sortable
import groovy.transform.ToString

def outputMap = [:]
def slaveBuildHistory = [:]
def slaveTimetable = [:]
def List<Thread> threads = []

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

def toDateString(time) {
  def millis = time.toInstant().toEpochMilli()

  def date = new Date(millis)
  def dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
  return dateFormat.format(date)
}

def getCurrentZonedTime() {
  def localTime = ZonedDateTime.now() 
  return localTime
}

def printInstanceInfo = { Slave slave ->

  def slaveName = slave.nodeName


  def sb = ''<<''
  outputMap << [(slaveName):sb]

  def allBuilds = slave.computer.builds
  slaveBuildHistory << [(slaveName):allBuilds]

  def timeline = new ArrayList<TimeFlag>()
  slaveTimetable << [(slaveName):timeline]

  sb <<'Slave Name:' + slaveName + '\n'

  def t = Thread.start {
    def strBuff = outputMap[slaveName]
    def computer = slave.getComputer()
    def builds = slaveBuildHistory[slaveName]

    def line = slaveTimetable[slaveName]
    def iter = builds.iterator()
    strBuff <<'all build count:' + iter.size() + '\n'

    // put each build timestamp to list
    builds.each {
	  strBuff << 'job name:' << it.parent.name << ':' << it.number << '\n'

      def start = it.getStartTimeInMillis()
      def end = start + it.duration

      line << new TimeFlag(time: start, isStartTime: true)
      line << new TimeFlag(time: end, isStartTime: false)
	}

    def sorted_timeline = line.sort(false)
	def newTimePeriod = new ArrayList<TimePeriod>()
	strBuff << 'timeline count:' << sorted_timeline.size() << '\n'

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
	strBuff << 'checked busy time period count:' << newTimePeriod.size() << '\n'

    def totalBusyTime = 0
    newTimePeriod.each {
      totalBusyTime += it.duration
    }

    strBuff << slaveName << ':totalBusyTime:' << totalBusyTime << ':' << formatDuration(totalBusyTime) << '\n'
  }
  threads << t
}

def run = { fromDateString, toDateString ->
  println toDateString(getCurrentZonedTime())

  Hudson.instance.slaves.each { printInstanceInfo(it) }

  threads.each { t ->
    t.join()
  }

  outputMap.each { k, v ->
    println v
  }
}
