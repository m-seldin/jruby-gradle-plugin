package com.github.jrubygradle

import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 */
class JRubyPluginSpec extends Specification {
    static final File TESTROOT = new File(System.getProperty('TESTROOT') ?: 'build/tmp/test/unittests')
    Project project

    def setup() {
        if (TESTROOT.exists()) {
            TESTROOT.deleteDir()
        }
        TESTROOT.mkdirs()
        project = ProjectBuilder.builder().build()
        project.apply plugin: 'com.github.jruby-gradle.base'
    }

    def "plugin should set repositories correctly"() {
        when:
        project.evaluate()

        then:
        hasRepositoryUrl(project, 'http://rubygems.lasagna.io/proxy/maven/releases')
    }

    def "setting the default repository via rubygemsRelease()"() {
        when:
        project.evaluate()

        then: "rubygemsRelease() should be defined"
        project.repositories.metaClass.respondsTo(project.repositories,'rubygemsRelease')

        and:
        hasRepositoryUrl(project, 'http://rubygems.lasagna.io/proxy/maven/releases')
    }

    private boolean hasRepositoryUrl(Project p, String url) {
        boolean result = false
        p.repositories.each { ArtifactRepository r ->
            if (r.url.toString() == url) {
                result = true
            }
        }
        return result
    }
}