package org.openarchive.telegram;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Validates precomposition references before passing untrusted Telegram TGS
 * animations to Lottie. Reject cyclic graphs and pathological nesting/size.
 * The separately bounded TgsPayload GZIP decoder remains Android-independent.
 */
final class TgsPrecompSafety {
    private static final int MAX_PRECOMPS = 512;
    private static final int MAX_REFERENCES = 8192;
    private static final int MAX_DEPTH = 48;

    private TgsPrecompSafety() {}

    static void requireAcyclic(JSONObject root) throws IOException {
        if (root == null) throw new IOException("TGS: отсутствует JSON-анимация");
        JSONArray assets = root.optJSONArray("assets");
        if (assets == null) return;

        Map<String, List<String>> graph = new HashMap<>();
        int totalReferences = 0;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            JSONArray layers = asset.optJSONArray("layers");
            if (layers == null) continue; // Image assets do not define precomps.
            String id = asset.optString("id", "");
            if (id.isEmpty() || graph.containsKey(id))
                throw new IOException("TGS: отсутствует или повторяется ID прекомпозиции");
            if (graph.size() >= MAX_PRECOMPS)
                throw new IOException("TGS: слишком много прекомпозиций");
            List<String> references = new ArrayList<>();
            for (int n = 0; n < layers.length(); n++) {
                JSONObject layer = layers.optJSONObject(n);
                if (layer != null && layer.optInt("ty", -1) == 0) {
                    String ref = layer.optString("refId", "");
                    if (ref.isEmpty()) throw new IOException("TGS: пустая ссылка прекомпозиции");
                    if (++totalReferences > MAX_REFERENCES)
                        throw new IOException("TGS: слишком много ссылок прекомпозиций");
                    references.add(ref);
                }
            }
            graph.put(id, references);
        }
        Map<String, Integer> colors = new HashMap<>();
        for (String id : graph.keySet()) visit(id, graph, colors, 0);
    }

    private static void visit(String id, Map<String, List<String>> graph,
                              Map<String, Integer> colors, int depth) throws IOException {
        if (depth > MAX_DEPTH)
            throw new IOException("TGS: превышена безопасная глубина прекомпозиций");
        int color = colors.containsKey(id) ? colors.get(id) : 0;
        if (color == 1)
            throw new IOException("TGS: циклическая ссылка между прекомпозициями");
        if (color == 2) return;
        colors.put(id, 1);
        for (String ref : graph.get(id)) {
            if (graph.containsKey(ref)) visit(ref, graph, colors, depth + 1);
        }
        colors.put(id, 2);
    }
}
