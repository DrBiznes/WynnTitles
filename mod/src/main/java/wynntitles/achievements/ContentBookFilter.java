package wynntitles.achievements;

import com.wynntils.core.text.StyledText;
import com.wynntils.handlers.container.ContainerQueryException;
import com.wynntils.handlers.container.scriptedquery.QueryStep;
import com.wynntils.handlers.container.scriptedquery.ScriptedContainerQuery;
import com.wynntils.handlers.container.type.ContainerContent;
import com.wynntils.handlers.container.type.ContainerContentChangeType;
import com.wynntils.models.containers.containers.ContentBookContainer;
import com.wynntils.utils.mc.LoreUtils;
import com.wynntils.utils.wynn.InventoryUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import wynntitles.WynnTitles;

/**
 * Puts the Content Book's filter back after a scan, which Wynntils does not do.
 * Queries run in the order they are started, so save, the scan and restore can
 * all be started at once. The slots and the number of filters are the ones
 * Wynntils uses in ContentBookQueries.
 */
final class ContentBookFilter {
	private static final int FILTER_SLOT = 66;
	private static final int PROGRESS_SLOT = 68;
	private static final int MAX_CLICKS = 12;

	private final DiscoveryScans scans;
	private String shown;
	private int clicks;

	ContentBookFilter(DiscoveryScans scans) {
		this.scans = scans;
	}

	void save() {
		ScriptedContainerQuery.builder("WynnTitles Save Content Book Filter")
				.onError(error -> WynnTitles.LOGGER.warn("Could not read the Content Book filter: {}", error))
				.then(openBook().processIncomingContainer(content -> scans.saveFilter(activeFilter(content))))
				.build()
				.executeQuery();
	}

	void restore() {
		clicks = 0;
		ScriptedContainerQuery.builder("WynnTitles Restore Content Book Filter")
				.onError(error -> {
					WynnTitles.LOGGER.warn("Could not restore the Content Book filter: {}", error);
					scans.restoreFailed(System.currentTimeMillis());
				})
				.then(openBook())
				.repeat(this::needsClick, QueryStep.clickOnSlot(FILTER_SLOT, () -> GLFW.GLFW_MOUSE_BUTTON_LEFT)
						.verifyContentChange(this::filterChanged))
				.execute(scans::filterRestored)
				.build()
				.executeQuery();
	}

	private static QueryStep openBook() {
		return QueryStep.useItemInHotbar(InventoryUtils.CONTENT_BOOK_SLOT_NUM).expectContainer(ContentBookContainer.class);
	}

	private boolean needsClick(ContainerContent content) throws ContainerQueryException {
		String target = scans.savedFilter();
		shown = activeFilter(content);
		if (target == null || target.equals(shown)) {
			return false;
		}
		if (shown == null) {
			throw new ContainerQueryException("Cannot read the active filter");
		}
		if (++clicks > MAX_CLICKS) {
			throw new ContainerQueryException("Filter was not found after " + MAX_CLICKS + " clicks");
		}
		return true;
	}

	/** The same check Wynntils makes: the book has refreshed and shows another filter. */
	private boolean filterChanged(
			ContainerContent content, Int2ObjectMap<ItemStack> changes, ContainerContentChangeType type) {
		return changes.containsKey(PROGRESS_SLOT) && !Objects.equals(activeFilter(content), shown);
	}

	private static String activeFilter(ContainerContent content) {
		return DiscoveryScans.activeFilter(LoreUtils.getLore(content.items().get(FILTER_SLOT)).stream()
				.map(StyledText::getString)
				.toList());
	}
}
