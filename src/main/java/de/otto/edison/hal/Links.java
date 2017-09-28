package de.otto.edison.hal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static de.otto.edison.hal.Link.linkBuilder;
import static de.otto.edison.hal.LinkRelations.emptyLinkRelations;
import static de.otto.edison.hal.LinkRelations.linkRelations;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Representation of a number of HAL _links.
 *
 * @see <a href="https://tools.ietf.org/html/draft-kelly-json-hal-08#section-4.1.1">draft-kelly-json-hal-08#section-4.1.1</a>
 * @since 0.1.0
 */
@JsonSerialize(using = Links.LinksSerializer.class)
@JsonDeserialize(using = Links.LinksDeserializer.class)
public class Links {

    public static final Set<String> DEFAULT_ARRAY_LINK_RELATIONS = unmodifiableSet(new HashSet<String>() {{
        add("curies");
        add("item");
        add("items");
    }});

    private static final String CURIES_REL = "curies";

    private static final Links EMPTY_LINKS = new Links();

    private final Map<String, List<Link>> links = new LinkedHashMap<>();
    private final LinkRelations linkRelations;

    private volatile Set<String> arrayRels = DEFAULT_ARRAY_LINK_RELATIONS;

    /**
     *
     * @since 0.1.0
     */
    Links() {
        this.linkRelations = emptyLinkRelations();
    }

    /**
     * <p>
     *     Creates a Links object from a map containing rel->List<Link>.
     * </p>
     * <p>
     *     If the links contain curies, the link-relation types are shortened to the curied format name:key.
     * </p>
     * <p>
     *     The list of links for a link-relation type must have the same {@link Link#rel}
     * </p>
     * <p>
     *     The {@link #arrayRels} Set contains the link-relation types that are serialized as an array event if
     *     there is only a single link. CURIs, for example, should always be contained in arrayRels.
     * </p>
     *
     * @param links a map with link-relation types as key and the list of links as value.
     * @param arrayRels the set of link-relation types that is rendered as an array of links.
     * @param linkRelations the LinkRelations used to CURI the link-relation types of the links.
     * @since 1.0.0
     */
    private Links(final Map<String, List<Link>> links,
                  final Set<String> arrayRels,
                  final LinkRelations linkRelations) {
        this.linkRelations = linkRelations;
        this.arrayRels = arrayRels;
        final List<Link> curies = links.getOrDefault(CURIES_REL, emptyList());
        curies.forEach(this.linkRelations::register);
        links.keySet().forEach(rel -> {
                this.links.put(linkRelations.resolve(rel), links.get(rel));
        });
    }

    /**
     * Applies LinkRelations to the links and replaces link-relation types with CURIed form, if applicable.
     * <p>
     *     All CURIes are registered in the given LinkRelations, so the HalRepresentation can forward these
     *     CURIes to embedded items.
     * </p>
     * @param linkRelations LinkRelations used to replace CURIed rels
     * @return Links having a reference to the given LinkRelations.
     */
    Links using(final LinkRelations linkRelations) {
        final List<Link> curies = links.getOrDefault(CURIES_REL, emptyList());
        curies.forEach(linkRelations::register);
        return new Links(links, arrayRels, linkRelations);
    }

    /**
     * Factory method used to create an empty Links instance.
     *
     * @return empty Links
     *
     * @since 0.1.0
     */
    public static Links emptyLinks() {
        return EMPTY_LINKS;
    }

    /**
     * Factory method used to build a Links instance from one or more {@link Link} objects.
     *
     * @param link a Link
     * @param more optionally more Links
     * @return Links
     *
     * @since 0.1.0
     */
    public static Links linkingTo(final Link link, final Link... more) {
        return linkingTo(new ArrayList<Link>() {{
            add(link);
            if (more != null) {
                addAll(asList(more));
            }
        }});
    }

    /**
     * Creates a Links object from a list of links with {@link #DEFAULT_ARRAY_LINK_RELATIONS} used to serialize _links
     * for a link-relation type as an array.
     *
     * @param links the list of links.
     * @return Links
     *
     * @since 0.2.0
     */
    public static Links linkingTo(final List<Link> links) {
        return linkingTo(links, DEFAULT_ARRAY_LINK_RELATIONS);
    }

    /**
     * Creates a Links object from a list of links with {@link #DEFAULT_ARRAY_LINK_RELATIONS} used to serialize _links
     * for a link-relation type as an array.
     *
     * @param links the list of links.
     * @param arrayRels the set of link-relation types that are always rendered as an array of links, even if there is
     *                  only a single link having the link-relation type. For example, 'curies' and 'item' rels should
     *                  always be arrays.
     * @return Links
     *
     * @since 1.0.0
     */
    public static Links linkingTo(final List<Link> links,
                                  final Set<String> arrayRels) {
        final Map<String,List<Link>> allLinks = new LinkedHashMap<>();
        links.forEach(l -> {
            if (!allLinks.containsKey(l.getRel())) {
                allLinks.put(l.getRel(), new ArrayList<>());
            }
            allLinks.get(l.getRel()).add(l);
        });
        return new Links(allLinks, arrayRels, emptyLinkRelations());
    }

    /**
     * Factory method used to build a Links.Builder.
     *
     * @return Links.Builder
     */
    public static Builder linksBuilder() {
        return new Builder();
    }

    /**
     * Factory method used to build a Links.Builder that is initialized from a prototype Links instance.
     *
     * @param prototype the prototype used to initialize the builder
     * @return Links.Builder
     */
    public static Builder copyOf(final Links prototype) {
        return new Builder().with(prototype).withArrayRels(prototype.arrayRels);
    }

    /**
     * Returns a Stream of links.
     *
     * @return Stream of Links
     */
    public Stream<Link> stream() {
        return links.values().stream()
                .flatMap(Collection::stream);
    }

    /**
     * Returns all link-relation types of the embedded items.
     *
     * @return set of link-relation types
     * @since 0.3.0
     */
    @JsonIgnore
    public Set<String> getRels() {
        return links.keySet();
    }

    /**
     * Returns the set of link-relation types that are always rendered as an array of links, event if there is only
     * a single link.
     * <p>
     *     The returned set only contains the configured 'arrayRels', not the set of link-relation types that actually
     *     contains an array as there are multiple links.
     * </p>
     *
     * @return set of link-relation types
     * @since 1.0.0
     */
    @JsonIgnore
    public Set<String> getArrayRels() {
        return arrayRels;
    }

    /**
     * <p>
     *     Returns the first (if any) link having the specified link-relation type.
     * </p>
     * <p>
     *     If CURIs are used to shorten custom link-relation types, it is possible to either use expanded link-relation types,
     *     or the CURI of the link-relation type. Using CURIs to retrieve links is not recommended, because it
     *     requires that the name of the CURI is known by clients.
     * </p>
     *
     * @param rel the link-relation type of the retrieved link.
     * @return optional link
     *
     * @see <a href="https://tools.ietf.org/html/draft-kelly-json-hal-08#section-8.2">draft-kelly-json-hal-08#section-8.2</a>
     * @since 0.1.0
     */
    public Optional<Link> getLinkBy(final String rel) {
        final List<Link> links = getLinksBy(rel);
        return links.isEmpty()
                ? Optional.empty()
                : Optional.of(links.get(0));
    }

    /**
     * <p>
     *     Returns the first (if any) link having the specified link-relation type and matching the given predicate.
     * </p>
     * <p>
     *     If CURIs are used to shorten custom link-relation types, it is possible to either use expanded link-relation types,
     *     or the CURI of the link-relation type. Using CURIs to retrieve links is not recommended, because it
     *     requires that the name of the CURI is known by clients.
     * </p>
     * <p>
     *     The Predicate is used to select one of possibly several links having the same link-relation type. See
     *     {@link LinkPredicates} for typical selections.
     * </p>
     *
     * @param rel the link-relation type of the retrieved link.
     * @param selector a predicate used to select one of possibly several links having the same link-relation type
     * @return optional link
     *
     * @see <a href="https://tools.ietf.org/html/draft-kelly-json-hal-08#section-8.2">draft-kelly-json-hal-08#section-8.2</a>
     * @since 1.0.0
     */
    public Optional<Link> getLinkBy(final String rel, final Predicate<Link> selector) {
        final List<Link> links = getLinksBy(rel, selector);
        return links.isEmpty()
                ? Optional.empty()
                : Optional.of(links.get(0));
    }

    /**
     * <p>
     *     Returns the list of links having the specified link-relation type.
     * </p>
     * <p>
     *     If CURIs are used to shorten custom link-relation types, it is possible to either use expanded link-relation types,
     *     or the CURI of the link-relation type. Using CURIs to retrieve links is not recommended, because it
     *     requires that the name of the CURI is known by clients.
     * </p>
     *
     * @param rel the link-relation type of the retrieved link.
     * @return list of matching link
     *
     * @see <a href="https://tools.ietf.org/html/draft-kelly-json-hal-08#section-8.2">draft-kelly-json-hal-08#section-8.2</a>
     * @since 0.1.0
     */
    public List<Link> getLinksBy(final String rel) {
        final String curiedRel = linkRelations.resolve(rel);
        final List<Link> links = this.links.get(curiedRel);

        return links != null ? links : emptyList();
    }

    /**
     * <p>
     *     Returns the list of links having the specified link-relation type and matching the given predicate.
     * </p>
     * <p>
     *     If CURIs are used to shorten custom link-relation types, it is possible to either use expanded link-relation types,
     *     or the CURI of the link-relation type. Using CURIs to retrieve links is not recommended, because it
     *     requires that the name of the CURI is known by clients.
     * </p>
     * <p>
     *     The Predicate is used to select some of possibly several links having the same link-relation type. See
     *     {@link LinkPredicates} for typical selections.
     * </p>
     *
     * @param rel the link-relation type of the retrieved link.
     * @param selector a predicate used to select some of the links having the specified link-relation type
     * @return list of matching link
     *
     * @see <a href="https://tools.ietf.org/html/draft-kelly-json-hal-08#section-8.2">draft-kelly-json-hal-08#section-8.2</a>
     * @since 1.0.0
     */
    public List<Link> getLinksBy(final String rel, final Predicate<Link> selector) {
        return getLinksBy(rel).stream().filter(selector).collect(toList());
    }

    /**
     *
     * @return true if Links is empty, false otherwise.
     *
     * @since 0.1.0
     */
    public boolean isEmpty() {
        return links.isEmpty();
    }

    /**
     * Configures the link-relation types that are always serialized as an array of links.
     *
     * @param arrayRels zero or more link-relation types
     * @return this
     * @since 1.0.0
     */
    public Links withArrayRels(final String... arrayRels) {
        this.arrayRels = arrayRels != null ? new HashSet<>(asList(arrayRels)) : emptySet();
        return this;
    }

    /**
     * Configures the link-relation types that are always serialized as an array of links.
     *
     * @param arrayRels a set of link-relation types
     * @return this
     * @since 1.0.0
     */
    public Links withArrayRels(final Set<String> arrayRels) {
        this.arrayRels = arrayRels != null ? arrayRels : emptySet();
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.1.0
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Links links1 = (Links) o;

        return links != null ? links.equals(links1.links) : links1.links == null;

    }

    /**
     * {@inheritDoc}
     *
     * @since 0.1.0
     */
    @Override
    public int hashCode() {
        return links != null ? links.hashCode() : 0;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.1.0
     */
    @Override
    public String toString() {
        return "Links{" +
                "links=" + links +
                '}';
    }

    /**
     * A Builder used to build Links instances.
     *
     * @since 0.2.0
     */
    public static class Builder {
        private final Map<String,List<Link>> links = new LinkedHashMap<>();
        private LinkRelations linkRelations = emptyLinkRelations();
        private Set<String> arrayRels = DEFAULT_ARRAY_LINK_RELATIONS;


        /**
         * Adds a list of links.
         * <p>
         *     {@link Link#isEquivalentTo(Link) Equivalent} links are NOT added but silently ignored.
         * </p>
         * @param links the list of links.
         * @return this
         *
         * @since 0.2.0
         */
        public Builder with(final List<Link> links) {
            links.forEach(l -> {
                if (!this.links.containsKey(l.getRel())) {
                    this.links.put(l.getRel(), new ArrayList<>());
                }
                final List<Link> linksPerRel = this.links.get(l.getRel());
                final boolean equivalentLinkExists = linksPerRel
                        .stream()
                        .anyMatch(link -> link.isEquivalentTo(l));
                if (!equivalentLinkExists) {
                    linksPerRel.add(l);
                }
            });
            return this;
        }

        /**
         * Adds one or more Links.
         * <p>
         *     {@link Link#isEquivalentTo(Link) Equivalent} links are NOT added but silently ignored.
         * </p>
         *
         * @param link a Link
         * @param more more links
         * @return this
         */
        public Builder with(final Link link, final Link... more) {
            with(new ArrayList<Link>() {{
                add(link);
                if (more != null) {
                    addAll(asList(more));
                }
            }});
            return this;
        }

        /**
         * Adds links from {@link Links}
         * <p>
         *     {@link Link#isEquivalentTo(Link) Equivalent} links are NOT added but silently ignored.
         * </p>
         * <p>
         *     The set of {@link #arrayRels} is <em>not</em> copied from 'moreLinks'.
         * </p>
         *
         * @param moreLinks the added links.
         * @return this
         *
         * @since 0.4.2
         */
        public Builder with(final Links moreLinks) {
            for (final String rel : moreLinks.getRels()) {
                with(moreLinks.getLinksBy(rel));
            }
            return this;
        }

        /**
         * Configures the set of link-relation types that are always rendered as an array of links.
         *
         * @param arrayRels Set of link-relation types
         * @return this
         * @since 1.0.0
         */
        public Builder withArrayRels(final Set<String> arrayRels) {
            this.arrayRels = arrayRels;
            return this;
        }

        /**
         * Configures the set of link-relation types that are always rendered as an array of links.
         *
         * @param arrayRels link-relation types
         * @return this
         * @since 1.0.0
         */
        public Builder withArrayRels(final String... arrayRels) {
            this.arrayRels = arrayRels != null ? new HashSet<>(asList(arrayRels)) : emptySet();
            return this;
        }

        public Builder using(final LinkRelations linkRelations) {
            this.linkRelations = linkRelations;
            return this;
        }

        /**
         * Creates a Links instance from all added links.
         *
         * @return Links
         */
        public Links build() {
            return new Links(links, arrayRels, linkRelations);
        }
    }

    /**
     * A Jackson JsonSerializer for Links. Used to render the _links part of HAL+JSON documents.
     *
     * @since 0.1.0
     */
    public static class LinksSerializer extends JsonSerializer<Links> {

        /**
         * {@inheritDoc}
         */
        @Override
        @SuppressWarnings("unchecked")
        public void serialize(final Links value, final JsonGenerator gen, final SerializerProvider serializers) throws IOException {
            gen.writeStartObject();
            for (final String rel : value.links.keySet()) {
                final List<Link> links = value.links.get(rel);
                if (links.size() > 1 || value.arrayRels.contains(rel)) {
                    gen.writeArrayFieldStart(rel);
                    for (final Link link : links) {
                        gen.writeObject(link);
                    }
                    gen.writeEndArray();
                } else {
                    gen.writeObjectField(rel, links.get(0));
                }
            }
            gen.writeEndObject();
        }

    }

    /**
     * A Jackson JsonDeserializer for Links. Used to parse the _links part of HAL+JSON documents.
     *
     * @since 0.1.0
     */
    public static class LinksDeserializer extends JsonDeserializer<Links> {

        private static final TypeReference<Map<String, ?>> TYPE_REF_LINK_MAP = new TypeReference<Map<String, ?>>() {};

        /**
         * {@inheritDoc}
         */
        @Override
        public Links deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            final Map<String,?> linksMap = p.readValueAs(TYPE_REF_LINK_MAP);
            final Map<String, List<Link>> links = linksMap
                    .entrySet()
                    .stream()
                    .collect(toMap(Map.Entry::getKey, e -> asListOfLinks(e.getKey(), e.getValue())));
            return new Links(
                    links,
                    DEFAULT_ARRAY_LINK_RELATIONS,
                    LinkRelations.linkRelations(links.getOrDefault(CURIES_REL, emptyList()))
            );
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private List<Link> asListOfLinks(final String rel, final Object value) {
            if (value instanceof Map) {
                return singletonList(asLink(rel, (Map)value));
            } else {
                try {
                    return ((List<Map>) value).stream().map(o -> asLink(rel, o)).collect(toList());
                } catch (final ClassCastException e) {
                    throw new IllegalStateException("Document is not in application/hal+json format. Expected a single Link or a List of Links: rel=" + rel + " value=" + value);
                }
            }
        }

        @SuppressWarnings("rawtypes")
        private Link asLink(final String rel, final Map value) {
            Link.Builder builder = linkBuilder(rel, value.get("href").toString())
                    .withHrefLang((String) value.get("hreflang"))
                    .withName((String) value.get("name"))
                    .withTitle((String) value.get("title"))
                    .withType((String) value.get("type"))
                    .withProfile((String) value.get("profile"))
                    .withDeprecation((String) value.get("deprecation"));
            return builder.build();
        }
    }
}
