package org.svis.generator.tests.rd.dynamix

import static org.junit.Assert.*
import org.junit.Test
import org.junit.BeforeClass
import java.io.File
import java.io.FileNotFoundException
import org.eclipse.emf.mwe2.launch.runtime.Mwe2Launcher
import org.apache.commons.io.FileUtils
import org.svis.generator.SettingsConfiguration

class Bank_X3DomMovingSpheresTest {
	
	@BeforeClass
	def static void launch() {
		SettingsConfiguration.getInstance("../org.svis.generator.tests/testdata/bank/input/BankMovingSpheresTest.properties")
		new Mwe2Launcher().run(#["../org.svis.generator.run/src/org/svis/generator/run/rd/Dynamix2RD.mwe2", "-p", "famixPath=testdata/bank/input/famixDyn",
			"dynamixPath=testdata/bank/input/dynamix","outputPath=output/rd/dynamix/bank/bank_x3dom_moving_spheres"])
	}
     
    @Test
    def testX3DOM() {
    	var File file1 = null
        var File file2 = null
        try {
            file1 = new File("./output/rd/dynamix/bank/bank_x3dom_moving_spheres/x3dom-model.html")
			file2 = new File("./testdata/bank/output/rd/dynamix/bank_x3dom_moving_spheres/x3dom-model.html")
        } catch (FileNotFoundException e) {
            e.printStackTrace
        }
        assertEquals(FileUtils.checksumCRC32(file1), FileUtils.checksumCRC32(file2))
    }
    
    @Test
	def testEventJS() {
		var File file1 = null
		var File file2 = null
		try {
			file1 = new File("./output/rd/dynamix/bank/bank_x3dom_moving_spheres/events.js")
			file2 = new File("./testdata/bank/output/rd/dynamix/bank_x3dom_moving_spheres/events.js")
		} catch (FileNotFoundException e) {
			e.printStackTrace
		}

		assertEquals(FileUtils.checksumCRC32(file1), FileUtils.checksumCRC32(file2))
	}
}