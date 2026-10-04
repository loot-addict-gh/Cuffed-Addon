package com.example.cuffedaddon.util;

/**
 * Reads float values out of Cuffed's own CuffedServerConfig (via
 * CuffedMod.SERVER_CONFIG) entirely through reflection.
 *
 * Cuffed's config fields are typed as lazrslib's own
 * com.lazrproductions.lazrslib.common.config.ConfigProperty<T> wrapper, and
 * lazrslib is NOT on this addon's compile classpath (only on the runtime
 * classpath, since Forge loads it as Cuffed's own dependency at runtime).
 * Referencing CuffedMod.SERVER_CONFIG.<field>.get() directly - even just to
 * read the value - fails to compile ("class file for ConfigProperty not
 * found"), because javac needs to resolve ConfigProperty's own class file
 * just to type-check the member access chain, regardless of the fact the
 * result is only ever used as a plain float afterwards. Full reflection
 * (Class.forName + Field/Method lookups, everything typed as Object until
 * the very last cast) is the only way to read these values without adding
 * lazrslib as a real compile dependency of this addon.
 *
 * If anything here ever breaks (Cuffed renames a field, changes the wrapper
 * class, etc.) this fails soft and returns the matching DEFAULT_* fallback
 * (Cuffed's own shipped default for that setting) instead of crashing world
 * load - anchoring stays fully functional, just with a bundled default
 * instead of the user's actual configured value in that edge case.
 */
public final class CuffedConfigBridge {

    private static final float DEFAULT_ANCHORING_SUFFOCATION_LENGTH = 12.0F;

    private CuffedConfigBridge() {
    }

    public static float getAnchoringSuffocationLength() {
        return readCuffedServerConfigFloat("ANCHORING_SUFFOCATION_LENGTH", DEFAULT_ANCHORING_SUFFOCATION_LENGTH);
    }

    private static float readCuffedServerConfigFloat(String fieldName, float fallback) {
        try {
            Class<?> cuffedModClass = Class.forName("com.lazrproductions.cuffed.CuffedMod");
            Object serverConfig = cuffedModClass.getField("SERVER_CONFIG").get(null);
            Object property = serverConfig.getClass().getField(fieldName).get(serverConfig);
            Object value = property.getClass().getMethod("get").invoke(property);
            return ((Number) value).floatValue();
        } catch (ReflectiveOperationException | ClassCastException e) {
            return fallback;
        }
    }
}
