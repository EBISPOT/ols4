package uk.ac.ebi.spot.ols.frontend;

import org.junit.jupiter.api.Test;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OntologyPageTest {

    @Test
    void testMinimalOntology(){
        WebDriver driver = new ChromeDriver();
        driver.get("http://localhost:3000/ontologies/owl2primer-minimal");


        WebElement ontologyIri = driver.findElement(By.id("ontologyIri"));
        System.out.println("ontologyIri = " + ontologyIri.getText());
        assertEquals("http://www.ebi.ac.uk/testcases/owl2primer/minimal.owl", ontologyIri.getText());

        WebElement versionIri = driver.findElement(By.id("versionIri"));
        System.out.println("versionIri = " + versionIri.getText());
        assertEquals("http://www.ebi.ac.uk/testcases/owl2primer/v.0.0.1/minimal.owl", versionIri.getText());

        WebElement ontologyId = driver.findElement(By.id("ontologyId"));
        System.out.println("ontologyId = " + ontologyId.getText());
        assertEquals("owl2primer-minimal", ontologyId.getText());

        WebElement version = driver.findElement(By.id("version"));
        System.out.println("version = " + version.getText());
        assertEquals("v.0.0.1", version.getText());

        WebElement numberOfEntities = driver.findElement(By.id("numberOfEntities"));
        System.out.println("numberOfEntities = " + numberOfEntities.getText());
        assertEquals("0", numberOfEntities.getText());

        driver.quit();
    }


}