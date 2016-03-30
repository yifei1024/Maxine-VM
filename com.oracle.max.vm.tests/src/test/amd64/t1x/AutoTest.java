package test.amd64.t1x;

import junit.framework.*;
import test.com.sun.max.vm.AllTests;

import com.sun.max.ide.*;

@org.junit.runner.RunWith(org.junit.runners.AllTests.class)
public final class AutoTest {
    private AutoTest() {
    }

    public static void main(String[] args) throws Exception {
        junit.textui.TestRunner.run(AutoTest.suite());
    }

    public static Test suite() throws Exception {
        final TestSuite suite = new TestCaseClassSet(AllTests.class).toTestSuite();
        suite.addTest(com.oracle.max.vm.ext.t1x.amd64.AllTests.suite());
        return suite;
    }
}
