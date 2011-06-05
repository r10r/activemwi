require 'time'

THIS_VERSION = '0.1'

layout = Layout.new
layout[:source, :main, :java] = 'src'
layout[:source, :main, :resources] = 'conf'
layout[:source, :test, :java] = 'test'

define 'activemwi', :layout=>layout do
  eclipse.natures 'org.eclipse.jdt.core.javanature'
  eclipse.builders 'org.eclipse.jdt.core.javabuilder'
  run.using :main => "de.codewheel.activemwi.ActiveMWI"

  compile.with(
    'org.asteriskjava:asterisk-java:jar:1.0.0.M3',
    transitive('commons-configuration:commons-configuration:jar:1.6'),
    transitive('org.slf4j:slf4j-log4j12:jar:1.6.1')
  )

  project.group = 'de.codewheel'
  project.version = THIS_VERSION
  package :sources
  package :javadoc
  package(:jar).with :manifest=>
  { 
    'Project' => project.id,
    'Copyright' => 'Ruben Jenster (C) 2011',
    'Version' => THIS_VERSION,
    'Creation' => Time.now.strftime("%a, %d %b %Y %H:%M:%S %z"),
    'Main-Class' => 'de.codewheel.activemwi.ActiveMWI'
  }
  
end

