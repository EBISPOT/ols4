package uk.ac.ebi.spot.ols.frontend;

import org.junit.jupiter.api.Test;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OntologyPageTest {

    @Test
    void testMinimalOntology(){
        WebDriver driver = new ChromeDriver();
        try {
            driver.get("http://localhost:3000/ontologies/owl2primer-minimal");

            WebElement ontologyIri = driver.findElement(By.id("ontologyIri"));
            WebElement versionIri = driver.findElement(By.id("versionIri"));
            WebElement version = driver.findElement(By.id("version"));
            WebElement ontologyId = driver.findElement(By.id("ontologyId"));
            WebElement numberOfEntities = driver.findElement(By.id("numberOfEntities"));
            assertAll(
                    () -> assertEquals("http://www.ebi.ac.uk/testcases/owl2primer/minimal.owl", ontologyIri.getText(),
                            "Incorrect ontologyIri"),
                    () -> assertEquals("http://www.ebi.ac.uk/testcases/owl2primer/v.0.0.1/minimal.owl", versionIri.getText()),
                    () -> assertEquals("v.0.0.1", version.getText(), "Incorrect version"),
                    () -> assertEquals("owl2primer-minimal", ontologyId.getText()),
                    () -> assertEquals("0", numberOfEntities.getText()));
        } catch (Throwable t) {
            t.printStackTrace();
        }

        driver.quit();
    }
}