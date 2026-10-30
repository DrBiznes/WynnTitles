package wynntitles.achievements;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VisitedTerritoriesTest {
	@Test
	void visitsBelongToOneCharacter() {
		VisitedTerritories visited = new VisitedTerritories(null);
		visited.add("archer", "Ragni");

		assertTrue(visited.contains("archer", "ragni"), "names ignore case");
		assertFalse(visited.contains("mage", "Ragni"));
	}

	@Test
	void contentBookNameCountsAsItsTerritory() {
		VisitedTerritories visited = new VisitedTerritories(null);
		visited.addAll("archer", List.of("Town of Lutho", "Detlas"));

		assertTrue(visited.contains("archer", "Lutho"));
		assertTrue(visited.contains("archer", "Detlas"));
	}

	@Test
	void visitsSurviveARestart(@TempDir Path dir) {
		Path file = dir.resolve("territories.json");
		new VisitedTerritories(file).add("archer", "The Moon");

		assertTrue(new VisitedTerritories(file).contains("archer", "The Moon"));
	}
}
