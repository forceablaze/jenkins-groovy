import hudson.util.RemotingDiagnostics;

//list of threads
//def List<Thread> threads = []

def outputMap = [:]

ruby_version = 'def proc = "ruby --version".execute(); proc.waitFor(); println proc.in.text';
python_version = 'def proc = ["python",  "-c", "import platform;print(platform.python_version())"].execute(); proc.waitFor(); println proc.in.text';
svn_version = 'def proc = "svn --version".execute(); proc.waitFor(); def str = proc.in.text; println str.substring(0, str.indexOf(10))';
print_ip = 'println InetAddress.localHost.hostAddress';
print_hostname = 'println InetAddress.localHost.canonicalHostName';

def printInstanceInfo = { Slave slave ->

  slaveName = slave.nodeName
  match = slaveName ==~ /osw-v0002.*/

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
        sb <<'\tip address:' + RemotingDiagnostics.executeGroovy(print_ip, channel)
        sb <<'\thost name:' + RemotingDiagnostics.executeGroovy(print_hostname, channel)
        sb <<'\tsvn info:' + RemotingDiagnostics.executeGroovy(svn_version, channel)
        sb <<'\tpython info:' + RemotingDiagnostics.executeGroovy(python_version, channel)
        sb <<'\truby info:' + RemotingDiagnostics.executeGroovy(ruby_version, channel)
      }
	  t.join()
  }
}

Hudson.instance.slaves.each { printInstanceInfo(it) }

outputMap.each { k, v ->
  println v
}
