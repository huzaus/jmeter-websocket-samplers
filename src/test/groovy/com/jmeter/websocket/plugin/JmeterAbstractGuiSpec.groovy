package com.jmeter.websocket.plugin

import spock.lang.Specification

import static org.apache.jmeter.util.JMeterUtils.initLocale
import static org.apache.jmeter.util.JMeterUtils.loadJMeterProperties
import static org.apache.jmeter.util.JMeterUtils.setJMeterHome

abstract class JmeterAbstractGuiSpec extends Specification {
    def setupSpec() {
        String jmeterHome = System.properties.'jmeter.home'
        setJMeterHome(jmeterHome)
        loadJMeterProperties("$jmeterHome/libexec/bin/jmeter.properties")
        initLocale()
    }
}
