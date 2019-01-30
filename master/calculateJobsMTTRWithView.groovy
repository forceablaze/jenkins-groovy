import hudson.Util;
import hudson.model.*;
import hudson.plugins.nested_view.NestedView;

import java.time.Duration
import java.text.SimpleDateFormat

def dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

def printlnJSONPrefix = { text ->
  println('JSONSTRING:' + text)
}

// hudson.model.FreeStyleBuild
def printBuildInfo = { Run build ->
  println 'Build number:' + build.number
  println 'Start time: ' + build.time

  if(build.result == null)
    println 'Status: ongoing'
  else
    println 'Status: ' + build.result

  println 'Duration: ' + build.getDurationString()
  println()
}

def formatDuration = { long duration ->
  return Util.getTimeSpanString(duration);
}

def calcAverageRecoveryTime = { totalFailedTime, buildCount ->
  if(buildCount == 0)
    return 0
  return totalFailedTime / buildCount
}

def printJobInfo = { Job job, fromDateString, toDateString ->
  println('job name:' + job.name)

  printlnJSONPrefix('      \"name\":\"' + job.name + '\",')
  printlnJSONPrefix('      \"' + job.name +'\":{')
  printlnJSONPrefix('        \"builds\":[')

  def fromDate = dateFormat.parse(fromDateString)
  def toDate = dateFormat.parse(toDateString)
  println "check build from " + fromDate + " to " + toDate

  long fromTime = fromDate.time
  long toTime = toDate.time

  // never null. The first entry is the latest build.
  def builds = job.getBuilds()
  def lastBuild = job.getLastBuild()

  def failedBuildTime = 0
  def failedBuildNumber = 0
  def totalRecoveryTime = 0
  def buildCount = 0
  def isFirst = true

  def firstToLastBuilds = builds.reverse()
  firstToLastBuilds.iterator().each {

	long startTime = it.startTimeInMillis
	if( startTime < fromTime || startTime > toTime ){
	  println "ignore this build " + it.number
	  return
	}

    def result = it.result
	if(result == null)
	  return
	
	// get the first FAILED build start time
	if(!result.equals(Result.SUCCESS)) {
	  if(failedBuildTime != 0)
	    return
      println "failed build: " + it.number
      failedBuildNumber = it.number
	  failedBuildTime = it.getStartTimeInMillis()
	  return
	}

    if(failedBuildTime == 0)
      return

	// SUCCESS
    if(isFirst)
	  isFirst = false
	else
      printlnJSONPrefix('          ,')

	recoveryTime = it.getStartTimeInMillis() - failedBuildTime
    println "recovery build: " + it.number
    println "recovery time from last failed build " + failedBuildNumber + ": " + formatDuration(recoveryTime)
	totalRecoveryTime += recoveryTime

    printlnJSONPrefix('          {')
    printlnJSONPrefix('            \"number\":' + it.number + ',')
    printlnJSONPrefix('            \"build_start_time\":' + it.getStartTimeInMillis() + ',')
    printlnJSONPrefix('            \"failed_build_time\":' + failedBuildTime + ',')
    printlnJSONPrefix('            \"recovery_time\":' + recoveryTime + ',')
    printlnJSONPrefix('            \"recovery_string\":\"' + formatDuration(recoveryTime) + '\",')
    printlnJSONPrefix('            \"total_recovery_time\":' + totalRecoveryTime + ',')

	failedBuildTime = 0
	buildCount++

    long mttr = calcAverageRecoveryTime(totalRecoveryTime, buildCount)
    def duration  = Duration.ofMillis(mttr)
    printlnJSONPrefix('            \"mttr\":' + mttr + ',')
    printlnJSONPrefix('            \"mttr_string\":\"' + formatDuration(duration.toMillis()) + '\"')
    printlnJSONPrefix('          }')
  }

  printlnJSONPrefix('        ],')

  println 'buildCount: ' + buildCount
  println 'totalRecoveryTime: ' + totalRecoveryTime
  long mttr = calcAverageRecoveryTime(totalRecoveryTime, buildCount)
  println 'mttr: ' + mttr
  def duration  = Duration.ofMillis(mttr)

  //println formatDuration(duration.toMillis())
  printlnJSONPrefix('        \"build_count\":' + buildCount + ',')
  if(lastBuild != null)
	  printlnJSONPrefix('        \"last_build_number\":' + lastBuild.number + ',')
  printlnJSONPrefix('        \"total_recovery_time\":' + totalRecoveryTime + ',')
  printlnJSONPrefix('        \"mttr\":' + mttr + ',')
  printlnJSONPrefix('        \"mttr_string\":\"' + formatDuration(duration.toMillis()) + '\"')
  printlnJSONPrefix('      }')
}

def getNestedView
getNestedView = { views, viewList ->
  println viewList.size


  if(viewList.size == 0)
    return views

  currentView = null
  views.each {
	if(it.name == viewList[0]) {
	  println 'match ' + it.name
	  viewList.remove(0)
	  currentView = it
    }
  }

  if(currentView instanceof NestedView) {
	return getNestedView(currentView.getViews(), viewList)
  }

  if(currentView instanceof ListView) {
    return currentView
  }
}

def run = { viewPath, fromDateString, toDateString ->

  def viewPathStr = ""
  for( def i = 0; i < viewPath.size; i++) {
    viewPathStr += viewPath[i]
	if(i + 1 < viewPath.size)
	  viewPathStr += '/'
  }

  view = getNestedView(Hudson.instance.getViews(), viewPath)

  println view.class

  printlnJSONPrefix('{')
  printlnJSONPrefix('  \"view_path\":\"' + viewPathStr + '\",')
  printlnJSONPrefix('  \"from_date\":\"' + fromDateString + '\",')
  printlnJSONPrefix('  \"to_date\":\"' + toDateString + '\",')
  printlnJSONPrefix('  \"jobs\" :[')

  list = view.getItems()
  for(def i = 0; i < list.size; i++) {
	it = list[i]
    printlnJSONPrefix('    {')


    printJobInfo(it, fromDateString, toDateString)

    if(i + 1 < list.size)
      printlnJSONPrefix('    },')
	else
      printlnJSONPrefix('    }')
  }

  printlnJSONPrefix('  ]')
  printlnJSONPrefix('}')
}
