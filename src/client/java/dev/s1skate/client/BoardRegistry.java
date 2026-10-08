package dev.s1skate.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.s1skate.S1Skate;
import dev.s1skate.client.sim.BoardDef;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Boards come from assets/s1skate/s1/boards.json in the local resource pack the converter builds from the
 * user's own Schedule I install. Without it there is one built-in board with approximate numbers and a
 * placeholder model.
 */
public final class BoardRegistry {
	private static final Logger LOG = LoggerFactory.getLogger("s1skate");
	private static final Identifier INDEX = Identifier.fromNamespaceAndPath(S1Skate.MOD_ID, "s1/boards.json");

	private static final List<BoardDef> boards = new ArrayList<>();
	private static final Map<String, BoardModel> models = new LinkedHashMap<>();
	private static boolean fromPack;
	private static int selected;

	private BoardRegistry() {
	}

	/** Re-reads the pack (cheap) so a resource reload or a re-run of the converter is picked up on mount. */
	public static void reload() {
		String previous = boards.isEmpty() ? null : current().key;
		boards.clear();
		models.clear();
		fromPack = false;
		var rm = Minecraft.getInstance().getResourceManager();
		Optional<Resource> res = rm.getResource(INDEX);
		if (res.isPresent()) {
			try (Reader r = res.get().openAsReader()) {
				JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
				JsonObject all = root.getAsJsonObject("boards");
				// the plain "board" (Schedule I's default skateboard) first
				if (all.has("board")) boards.add(BoardDef.fromJson("board", all.getAsJsonObject("board")));
				for (var e : all.entrySet()) {
					if (!e.getKey().equals("board")) boards.add(BoardDef.fromJson(e.getKey(), e.getValue().getAsJsonObject()));
				}
				fromPack = !boards.isEmpty();
			} catch (Exception e) {
				LOG.error("Could not read {}", INDEX, e);
				boards.clear();
			}
		}
		if (boards.isEmpty()) boards.add(new BoardDef());
		selected = 0;
		if (previous != null) {
			for (int i = 0; i < boards.size(); i++) if (boards.get(i).key.equals(previous)) selected = i;
		}
	}

	public static boolean isFromPack() {
		return fromPack;
	}

	public static BoardDef current() {
		if (boards.isEmpty()) reload();
		return boards.get(Math.min(selected, boards.size() - 1));
	}

	public static BoardDef cycle() {
		if (boards.isEmpty()) reload();
		selected = (selected + 1) % boards.size();
		return current();
	}

	public static BoardModel model(BoardDef def) {
		return models.computeIfAbsent(def.key, k -> loadModel(def));
	}

	private static BoardModel loadModel(BoardDef def) {
		if (def.model == null) return BoardModel.placeholder();
		Identifier id = Identifier.fromNamespaceAndPath(S1Skate.MOD_ID, "s1/" + def.model);
		var res = Minecraft.getInstance().getResourceManager().getResource(id);
		if (res.isEmpty()) return BoardModel.placeholder();
		try (Reader r = res.get().openAsReader()) {
			JsonArray parts = JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("parts");
			return BoardModel.fromJson(parts);
		} catch (Exception e) {
			LOG.error("Could not read board model {}", id, e);
			return BoardModel.placeholder();
		}
	}
}
