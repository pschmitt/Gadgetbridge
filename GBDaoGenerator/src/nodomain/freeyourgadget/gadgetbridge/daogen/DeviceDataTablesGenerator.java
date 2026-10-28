package nodomain.freeyourgadget.gadgetbridge.daogen;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import freemarker.template.Template;

import de.greenrobot.daogenerator.Entity;
import de.greenrobot.daogenerator.Property;
import de.greenrobot.daogenerator.Schema;
import de.greenrobot.daogenerator.ToMany;
import de.greenrobot.daogenerator.ToManyBase;
import de.greenrobot.daogenerator.ToOne;

/**
 * Generates the DeviceDataTables class, which deletes and queries the rows of all entities that
 * have a relation path to the device entity.
 */
public class DeviceDataTablesGenerator {
    /**
     * A column of a child entity that references a column of a parent entity.
     */
    private record ParentLink(Entity parent, Property childProperty, Property parentProperty) {
    }

    private final Schema schema;
    private final Entity device;

    public DeviceDataTablesGenerator(final Schema schema, final Entity device) {
        this.schema = schema;
        this.device = device;
    }

    /**
     * Validate that every deviceId property has a declared relation to the device entity.
     */
    public void validate() {
        final List<String> violations = new ArrayList<>();
        for (final Entity entity : schema.getEntities()) {
            for (final Property property : entity.getProperties()) {
                if (!"deviceId".equals(property.getPropertyName())) {
                    continue;
                }
                boolean linked = false;
                for (final ToOne toOne : entity.getToOneRelations()) {
                    linked |= toOne.getTargetEntity() == device && toOne.getFkProperties()[0] == property;
                }
                for (final ToManyBase toManyBase : entity.getIncomingToManyRelations()) {
                    linked |= toManyBase instanceof final ToMany toMany
                        && toMany.getSourceEntity() == device
                        && toMany.getTargetProperties()[0] == property;
                }
                if (!linked) {
                    violations.add(entity.getClassName());
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException("deviceId without a relation to Device: " + violations);
        }
    }

    /**
     * Writes the DeviceDataTables class.
     *
     * @throws IllegalStateException if there is a relation cycle in the schema
     */
    public void generate(final Template template, final String outputDir) throws Exception {
        final Map<Entity, List<ParentLink>> parentLinks = collectParentLinks(schema);
        final Map<Entity, Integer> depths = new HashMap<>();
        for (final Entity entity : schema.getEntities()) {
            deviceDepth(entity, parentLinks, depths, new LinkedHashSet<>());
        }

        final Map<Entity, List<ParentLink>> deviceLinks = new HashMap<>();
        for (final Map.Entry<Entity, Integer> entry : depths.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            final List<ParentLink> links = new ArrayList<>();
            for (final ParentLink link : parentLinks.get(entry.getKey())) {
                if (link.parent() == device || depths.get(link.parent()) > 0) {
                    links.add(link);
                }
            }
            deviceLinks.put(entry.getKey(), links);
        }

        final Comparator<Entity> byDepth = Comparator.comparing(depths::get);
        final List<Entity> parentsFirst = new ArrayList<>(deviceLinks.keySet());
        parentsFirst.sort(byDepth.thenComparing(Entity::getTableName));
        final List<Entity> childrenFirst = new ArrayList<>(deviceLinks.keySet());
        childrenFirst.sort(byDepth.reversed().thenComparing(Entity::getTableName));

        final List<String[]> deleteStatements = new ArrayList<>();
        for (final Entity entity : childrenFirst) {
            deleteStatements.add(new String[]{
                entity.getTableName(),
                "DELETE FROM " + quote(entity.getTableName()) + " WHERE " + deviceCondition(entity, deviceLinks)
            });
        }
        final List<String[]> selectStatements = new ArrayList<>();
        selectStatements.add(new String[]{
            device.getTableName(),
            "SELECT * FROM " + quote(device.getTableName()) + " WHERE " + quote(device.getPkProperty().getColumnName()) + " = ?1"
        });
        for (final Entity entity : parentsFirst) {
            selectStatements.add(new String[]{
                entity.getTableName(),
                "SELECT * FROM " + quote(entity.getTableName()) + " WHERE " + deviceCondition(entity, deviceLinks)
            });
        }
        final List<String[]> orphanStatements = new ArrayList<>();
        for (final Entity entity : parentsFirst) {
            orphanStatements.add(new String[]{
                entity.getTableName(),
                "DELETE FROM " + quote(entity.getTableName()) + " WHERE " + orphanCondition(deviceLinks.get(entity))
            });
        }

        final File packageDir = new File(outputDir + "/nodomain/freeyourgadget/gadgetbridge/entities");
        //noinspection ResultOfMethodCallIgnored
        packageDir.mkdirs();
        final Map<String, Object> root = new HashMap<>();
        root.put("deleteStatements", deleteStatements);
        root.put("selectStatements", selectStatements);
        root.put("orphanStatements", orphanStatements);
        final StringWriter rendered = new StringWriter();
        template.process(root, rendered);
        final File file = new File(packageDir, "DeviceDataTables.java");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, false))) {
            writer.write(rendered.toString().replaceAll("\\R", System.lineSeparator()));
        }
        System.out.println("Written " + file.getName() + " with " + deleteStatements.size() + " tables");
    }

    private static Map<Entity, List<ParentLink>> collectParentLinks(final Schema schema) {
        final Map<Entity, List<ParentLink>> parentLinks = new HashMap<>();
        for (final Entity entity : schema.getEntities()) {
            parentLinks.computeIfAbsent(entity, k -> new ArrayList<>());
        }
        for (final Entity entity : schema.getEntities()) {
            for (final ToOne toOne : entity.getToOneRelations()) {
                parentLinks.get(entity).add(new ParentLink(
                    toOne.getTargetEntity(),
                    toOne.getFkProperties()[0],
                    toOne.getTargetEntity().getPkProperty()
                ));
            }
            for (final ToManyBase toManyBase : entity.getToManyRelations()) {
                if (!(toManyBase instanceof final ToMany toMany)) {
                    continue;
                }
                if (toMany.getSourceProperties().length != 1) {
                    throw new IllegalStateException("Only single column relations are supported: " + toMany);
                }
                parentLinks.get(toMany.getTargetEntity()).add(new ParentLink(
                    entity,
                    toMany.getTargetProperties()[0],
                    toMany.getSourceProperties()[0]
                ));
            }
        }
        return parentLinks;
    }

    /**
     * Returns the length of the longest relation path from the entity to the device entity, or -1
     * if the entity has no path to the device entity.
     */
    private int deviceDepth(final Entity entity,
                            final Map<Entity, List<ParentLink>> parentLinks,
                            final Map<Entity, Integer> depths,
                            final Set<Entity> visiting) {
        if (entity == device) {
            return 0;
        }
        final Integer known = depths.get(entity);
        if (known != null) {
            return known;
        }
        if (!visiting.add(entity)) {
            throw new IllegalStateException("Relation cycle at " + entity.getClassName());
        }
        int depth = -1;
        for (final ParentLink link : parentLinks.get(entity)) {
            final int parentDepth = deviceDepth(link.parent(), parentLinks, depths, visiting);
            if (parentDepth >= 0) {
                depth = Math.max(depth, parentDepth + 1);
            }
        }
        visiting.remove(entity);
        depths.put(entity, depth);
        return depth;
    }

    private static String quote(final String identifier) {
        return "\"" + identifier + "\"";
    }

    /**
     * Returns an SQL condition that matches the rows of the entity that belong to the device in
     * parameter ?1.
     */
    private String deviceCondition(final Entity entity, final Map<Entity, List<ParentLink>> deviceLinks) {
        final List<String> conditions = new ArrayList<>();
        for (final ParentLink link : deviceLinks.get(entity)) {
            final String column = quote(link.childProperty().getColumnName());
            if (link.parent() == device) {
                conditions.add(column + " = ?1");
            } else {
                conditions.add(column + " IN (SELECT " + quote(link.parentProperty().getColumnName()) +
                    " FROM " + quote(link.parent().getTableName()) +
                    " WHERE " + deviceCondition(link.parent(), deviceLinks) + ")");
            }
        }
        return String.join(" OR ", conditions);
    }

    /**
     * Returns an SQL condition that matches the rows of the entity whose parent rows do not exist.
     * A row with only null references does not match.
     */
    private static String orphanCondition(final List<ParentLink> links) {
        if (links.size() == 1) {
            final ParentLink link = links.getFirst();
            return quote(link.childProperty().getColumnName()) + " NOT IN (SELECT " +
                quote(link.parentProperty().getColumnName()) + " FROM " + quote(link.parent().getTableName()) + ")";
        }
        final List<String> notNull = new ArrayList<>();
        final List<String> dangling = new ArrayList<>();
        for (final ParentLink link : links) {
            final String column = quote(link.childProperty().getColumnName());
            notNull.add(column + " IS NOT NULL");
            dangling.add("(" + column + " IS NULL OR " + orphanCondition(List.of(link)) + ")");
        }
        return "(" + String.join(" OR ", notNull) + ") AND " + String.join(" AND ", dangling);
    }
}
