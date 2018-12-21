import hudson.util.RemotingDiagnostics;

//list of threads
//def List<Thread> threads = []
List<StringBuffer> output = []

def outputMap = [:]
def remoteOutputMap = [:]

svn_version = 'def proc = "svn --version".execute(); proc.waitFor(); println proc.in.text';
print_ip = 'println InetAddress.localHost.hostAddress';
print_hostname = 'println InetAddress.localHost.canonicalHostName';

def printInstanceInfo = { Slave slave ->

  slaveName = slave.nodeName
  match = slaveName ==~ /private-pc.*/

  if(!match)
    return

    sb = ''<<''
    outputMap << [(slave.nodeName):sb]

    sb <<'Slave Name:' + slave.nodeName + '\n'
//    sb <<slave.class + ''

    computer = slave.getComputer()
    sb <<'\tis offline:' + computer.isOffline() + '\n'

    if(!computer.offline) {
      channel = slave.getChannel()

      t = Thread.start {
        sb <<'\tip address:' + RemotingDiagnostics.executeGroovy(print_ip, channel) + '\n'
        sb <<'\thost name:' + RemotingDiagnostics.executeGroovy(print_hostname, channel) + '\n'
      //println(RemotingDiagnostics.executeGroovy(svn_version, channel))
      }
	  t.join()
  }
}

Hudson.instance.slaves.each { printInstanceInfo(it) }

outputMap.each { k, v ->
  println v
}
