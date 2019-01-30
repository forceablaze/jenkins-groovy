import hudson.Util;
import hudson.model.*;
import hudson.plugins.nested_view.NestedView;

import java.time.Duration
import java.text.SimpleDateFormat

def dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

def printlnJSONPrefix = { text ->
  println('JSONSTRING:' + text)
}

def printJSONPrefix = { text ->
  print('JSONSTRING:' + text)
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

def calcRate = { x, y ->
  if(y == 0)
    return 0
  return Math.round(x/y * 10000) / 10000
}

// yyyyMMddHHmmss
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

  def failedBuildCount = 0
  def successBuildCount = 0
  def buildCount = 0

  def firstToLastBuilds = builds.reverse()
  def idx = -1
  def length = builds.size
  def isFirst = true
  firstToLastBuilds.iterator().each {

	long startTime = it.startTimeInMillis

	if( startTime < fromTime || startTime > toTime ){
	  println "ignore this build " + it.number
	  return
	}

    def result = it.result
	if(result == null)
	  return

    if(isFirst)
	  isFirst = false
	else
      printlnJSONPrefix('          ,')

    printlnJSONPrefix('          {')
    printlnJSONPrefix('            \"number\":' + it.number + ',')
    printlnJSONPrefix('            \"result\":\"' + it.result + '\",')

	buildCount += 1
    def sb = ''<<''
	def date = new Date(startTime)
	sb << it.number << " " << it.result << " " << date
	println sb
 	
	// success build
	if(result.equals(Result.SUCCESS))
	  successBuildCount += 1
	// failed build
	else
	  failedBuildCount += 1

    def failed_rate = calcRate(failedBuildCount, buildCount)
    def success_rate = calcRate(successBuildCount, buildCount)
    printlnJSONPrefix('            \"build_count\":' + buildCount + ',')
    printlnJSONPrefix('            \"failed_build_count\":' + failedBuildCount + ',')
    printlnJSONPrefix('            \"success_build_count\":' + successBuildCount + ',')
    printlnJSONPrefix('            \"failed_rate\":' + failed_rate + ',')
    printlnJSONPrefix('            \"success_rate\":' + success_rate)

    printlnJSONPrefix('          }')
  }


/*
  def failed_rate = 0
  def success_rate = 0
  if(buildCount == 0)
    println '稼働率: 0%'
  else {
    failed_rate = Math.round(failedBuildCount/buildCount * 10000) / 10000
    success_rate = Math.round(successBuildCount/buildCount * 10000) / 10000
    //println '稼働率: ' + Math.round(failedBuildCount/buildCount * 10000) / 100 + "%"
  }
*/
  def failed_rate = calcRate(failedBuildCount, buildCount)
  def success_rate = calcRate(successBuildCount, buildCount)

  printlnJSONPrefix('        ],')

  printlnJSONPrefix('        \"build_count\":' + buildCount + ',')
  if(lastBuild != null)
	  printlnJSONPrefix('        \"last_build_number\":' + lastBuild.number + ',')
  printlnJSONPrefix('        \"failed_count\":' + failedBuildCount + ',')
  printlnJSONPrefix('        \"success_count\":' + successBuildCount + ',')
  printlnJSONPrefix('        \"failed_rate\":' + failed_rate + ',')
  printlnJSONPrefix('        \"success_rate\":' + success_rate)

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

def run = { viewPath, fromDateString = "19000101000000", toDateString = "20190123000000" ->

  def viewPathStr = ""
  for( def i = 0; i < viewPath.size; i++) {
    viewPathStr += viewPath[i] 
	if(i + 1 < viewPath.size)
	  viewPathStr += '/'
  }

  view = getNestedView(Hudson.instance.getViews(), viewPath)


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
