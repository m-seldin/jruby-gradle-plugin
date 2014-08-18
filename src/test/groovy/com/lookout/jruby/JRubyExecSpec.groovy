package com.lookout.jruby

import org.gradle.api.tasks.TaskInstantiationException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.*

/**
 * @author Schalk W. Cronjé
 */
class JRubyExecSpec extends Specification {

    static final boolean TESTS_ARE_OFFLINE = System.getProperty('TESTS_ARE_OFFLINE') != null
    static final File TEST_SCRIPT_DIR = new File( System.getProperty('TESTS_SCRIPT_DIR') ?: 'src/test/resources/scripts')
    static final File TESTROOT = new File(System.getProperty('TESTROOT') ?: 'build/tmp/test/unittests')
    static final String TASK_NAME = 'RubyWax'

    def project
    def execTask

    void setup() {
        project = ProjectBuilder.builder().build()
        project.buildDir = TESTROOT
        project.apply plugin: 'com.lookout.jruby'
        execTask = project.task(TASK_NAME,type: JRubyExec)
    }

    def "Do not allow JRubyExec to be instantiated if plugin has not been loaded"() {
        given: "A basic project"
            def badProject = ProjectBuilder.builder().build()

        when: "A JRubyExec task is instantiated with the jruby plugin being applied"
            badProject.task( 'bad', type : JRubyExec )

        then: "An exception should be thrown"
            thrown(TaskInstantiationException)
    }

    def "Do not allow args to be set directly"() {

        when: "Calling args"
            execTask.args ( 'a param','b param')

        then: "An exception should be thrown instead of JavaExec.args being set"
            thrown(UnsupportedOperationException)

    }

    def "Check jruby defaults"() {

        expect: "Default jruby version should be same as project.ruby.execVersion"
            execTask.jrubyVersion == project.jruby.execVersion

        and: "Default configuration should be jrubyExec"
            execTask.jrubyConfigurationName == 'jrubyExec'
    }

    def "Check jruby defaults when jruby.execVersion is changed after the task is created"() {

        given:
            final def String initialVersion= project.jruby.execVersion

        when: "ExecVersion is changed later on, and JRubyExec.jrubyVersion was not called"
            project.jruby.execVersion = '1.5.0'

        then: "jruby defaults version should point to the earlier version"
            execTask.jrubyVersion == '1.5.0'

        and: "Default configuration should be jrubyExec"
            execTask.jrubyConfigurationName == 'jrubyExec'
    }

    def "Changing the jruby version on a JRubyExec task"() {
        given:
            final String cfgName = 'jrubyExec$$' + TASK_NAME

        when: "Version is set on the task"
            final String newVersion = '1.7.11'
            assert project.jruby.execVersion != newVersion
            execTask.jrubyVersion = newVersion
            project.evaluate()

        then: "jrubyVersion must be updated"
            execTask.jrubyVersion == newVersion

        and: "jrubyConfigurationName must point to this new configuration"
            cfgName == execTask.jrubyConfigurationName

        and: "configuration must exist"
            project.configurations.getByName(cfgName) != null
    }

    @IgnoreIf({TESTS_ARE_OFFLINE})
    def "Changing the jruby version will load the correct jruby"() {
        when: "Version is set on the task"
            final String newVersion = '1.7.11'
            assert project.jruby.execVersion != newVersion
            execTask.jrubyVersion = newVersion
            project.evaluate()

            def jarName = project.configurations.getByName('jrubyExec$$'+TASK_NAME).files.find { it.toString().find('jruby-complete') }
            def matches = jarName ? (jarName =~ /.*(jruby-complete-.+.jar)/ ) : null

        then: "jruby-complete-${newVersion}.jar must be selected"
            jarName != null
            matches != null
            "jruby-complete-${newVersion}.jar".toString() ==  matches[0][1]
    }

    def "Checking the jruby main class"() {
        expect:
            execTask.main == 'org.jruby.Main'
    }

    def "Setting the script name"() {
        when: 'Setting path to a string'
            execTask.script = "${TEST_SCRIPT_DIR}/helloWorld.rb"

        then: 'script will be File object with the correct path'
            execTask.script.absolutePath == new File(TEST_SCRIPT_DIR,'helloWorld.rb').absolutePath
    }

    def "Setting jruby arguments"()  {
        when: "calling scriptArgs multiple times, with different kinds of arguments"
            project.configure(execTask) {
                jrubyArgs 'a', 'b', 'c'
                jrubyArgs 'd', 'e', 'f'
            }

        then: "append everything"
            execTask.jrubyArgs() == ['a','b','c','d','e','f']
    }

    def "Setting script arguments"()  {
        when: "calling scriptAtgs multiple times, with different kinds of arguments"
            project.configure(execTask) {
                scriptArgs 'a', 'b', 'c'
                scriptArgs 'd', 'e', 'f'
            }
        then: "append everything"
            execTask.scriptArgs() == ['a','b','c','d','e','f']
    }

    def "Getting correct command-line passed"() {
        when:
            project.configure(execTask) {
                scriptArgs '-s1','-s2','-s3'
                jrubyArgs  '-j1','-j2','-j3'
                script     "${TEST_SCRIPT_DIR}/helloWorld.rb"
            }

        then:
            execTask.getArgs() == ['-j1','-j2','-j3',new File(TEST_SCRIPT_DIR,'helloWorld.rb').absolutePath,'-s1','-s2','-s3']
    }

    @IgnoreIf({TESTS_ARE_OFFLINE})
    def "Running a Hello World script"() {
        given:
            def output = new ByteArrayOutputStream()
            project.configure(execTask) {
                script        "${TEST_SCRIPT_DIR}/helloWorld.rb"
                standardOutput output
            }

        when:
            project.evaluate()
            execTask.exec()

        then:
            output.toString() == "Hello, World\n"
    }

    @IgnoreIf({TESTS_ARE_OFFLINE})
    def "Running a script that requires a gem"() {
        given:
            def output = new ByteArrayOutputStream()
            project.configure(execTask) {
                setEnvironment [:]
                script        "${TEST_SCRIPT_DIR}/requiresGem.rb"
                standardOutput output
            }

        when:
            project.dependencies.add(JRubyExec.JRUBYEXEC_CONFIG,'rubygems:credit_card_validator:1.2.0' )
            project.evaluate()
            execTask.exec()

        then:
            output.toString() == "Not valid\n"
    }

    @IgnoreIf({TESTS_ARE_OFFLINE})
    def "Running a script that requires a gem, a separate jRuby and a separate configuration"() {
        given:
            def output = new ByteArrayOutputStream()
            project.with {
                configurations.create('RubyWax')
                dependencies.add('RubyWax','rubygems:credit_card_validator:1.1.0')
                configure(execTask) {
                    script        "${TEST_SCRIPT_DIR}/requiresGem.rb"
                    standardOutput output
                    jrubyVersion   '1.7.11'
                    configuration 'RubyWax'
                }
            }


        when:
            project.evaluate()
            execTask.exec()

        then:
            output.toString() == "Not valid\n"
    }
}
