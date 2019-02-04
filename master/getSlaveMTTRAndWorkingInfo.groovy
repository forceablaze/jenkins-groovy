import hudson.util.RemotingDiagnostics;
import hudson.Util;
import java.time.Duration
import java.time.ZonedDateTime
import java.text.SimpleDateFormat
import java.util.Calendar
import groovy.transform.Sortable
import groovy.transform.ToString

//list of threads
//def List<Thread> threads = []

def outputMap = [:]
def slaveBuildHistory = [:]
def slaveTimetable = [:]
def List<Thread> threads = []

print_ip = 'println InetAddress.localHost.hostAddress';
print_hostname = 'println InetAddress.localHost.canonicalHostName';


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

  def buildTimeLine = slave.computer.timeline
  slaveBuildHistory << [(slaveName):buildTimeLine]

  def timeline = new ArrayList<TimeFlag>()
  slaveTimetable << [(slaveName):timeline]

  sb <<'Slave Name:' + slaveName + '\n'

  def t = Thread.start {
    def strBuff = outputMap[slaveName]
    def computer = slave.getComputer()
    def builds = slaveBuildHistory[slaveName].builds

    def line = slaveTimetable[slaveName]
    def iter = builds.iterator()
    strBuff <<'build time line count:' + iter.size() + '\n'

    builds.each {
	  strBuff << 'job name:' << it.parent.name << ':' << it.number << '\n'

      def startTimeInMillis = it.getStartTimeInMillis()
      def timeInMillis = it.getTimeInMillis()

      def period = new TimePeriod(start: startTimeInMillis, duration: it.duration)

      line << new TimeFlag(time: period.start, isStartTime: true)
      line << new TimeFlag(time: period.end, isStartTime: false)
	}

    def sorted_timeline = line.sort(false)
	def newTimePeriod = new ArrayList<TimePeriod>()
	strBuff << 'timeline count:' << sorted_timeline.size() << '\n'

    def depthCount = 0
	def switchOn = true
	def startTime = 0
    for(def idx = 0; idx < sorted_timeline.size(); idx++) {
      def item = sorted_timeline[idx]
      strBuff <<'timetable:'<< item << ':' << depthCount << ':' << switchOn << '\n'

      if(item.isStartTime) {

        if(switchOn)
          startTime = item.time
          //newTimeline << item

        if(depthCount == 0)
          switchOn = false
        depthCount += 1
      }
      else {
        depthCount -= 1

		if(depthCount == 0)
          switchOn = true

        if(switchOn)
          newTimePeriod << new TimePeriod(start: startTime, duration: item.time - startTime)
      }
	}
	strBuff << 'checked timeline count:' << newTimePeriod.size() << '\n'

    def totalBusyTime = 0
    newTimePeriod.each {
      strBuff <<'checked timetable:' + it + '\n'
      totalBusyTime += it.duration
    }

    strBuff << 'totalBusyTime' << totalBusyTime << ':' << formatDuration(totalBusyTime) << '\n'
  }
  threads << t
}

def run = {
  println toDateString(getCurrentZonedTime())

  Hudson.instance.slaves.each { printInstanceInfo(it) }

  threads.each { t ->
    t.join()
  }

  outputMap.each { k, v ->
    println v
  }
}
