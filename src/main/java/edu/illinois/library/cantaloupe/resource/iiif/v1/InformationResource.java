package edu.illinois.library.cantaloupe.resource.iiif.v1;

import java.io.FileNotFoundException;
import java.util.List;

import edu.illinois.library.cantaloupe.RestletApplication;
import edu.illinois.library.cantaloupe.cache.CacheFacade;
import edu.illinois.library.cantaloupe.config.Configuration;
import edu.illinois.library.cantaloupe.config.Key;
import edu.illinois.library.cantaloupe.image.Format;
import edu.illinois.library.cantaloupe.image.Identifier;
import edu.illinois.library.cantaloupe.processor.Processor;
import edu.illinois.library.cantaloupe.processor.ProcessorFactory;
import edu.illinois.library.cantaloupe.resolver.Resolver;
import edu.illinois.library.cantaloupe.resolver.ResolverFactory;
import edu.illinois.library.cantaloupe.resource.JSONRepresentation;
import edu.illinois.library.cantaloupe.processor.ProcessorConnector;
import edu.illinois.library.cantaloupe.resource.RequestContext;
import org.restlet.Request;
import org.restlet.data.Header;
import org.restlet.data.MediaType;
import org.restlet.data.Preference;
import org.restlet.data.Reference;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.util.Series;

/**
 * Handles IIIF Image API 1.x information requests.
 *
 * @see <a href="http://iiif.io/api/image/1.1/#image-info-request">Information
 * Requests</a>
 */
public class InformationResource extends IIIF1Resource {

    /**
     * Redirects <code>/{identifier}</code> to
     * <code>/{identifier}/info.json</code>, respecting the Servlet context
     * root and {@link #PUBLIC_IDENTIFIER_HEADER} header.
     */
    public static class RedirectingResource extends IIIF1Resource {
        @Get
        public Representation doGet() {
            final Request request = getRequest();
            final Reference newRef = new Reference(
                    getPublicRootRef(request.getRootRef(), request.getHeaders()) +
                            RestletApplication.IIIF_1_PATH + "/" +
                            getPublicIdentifier() +
                            "/info.json");
            redirectSeeOther(newRef);
            return new EmptyRepresentation();
        }
    }

    /**
     * Responds to information requests.
     *
     * @return {@link ImageInfo} instance serialized to JSON.
     */
    @Get
    public Representation doGet() throws Exception {
        final Identifier identifier = getIdentifier();
        final Resolver resolver = new ResolverFactory().newResolver(identifier);

        // Setup the resolver context.
        final RequestContext requestContext = new RequestContext();
        requestContext.setRequestURI(getReference().toString());
        requestContext.setRequestHeaders(getRequest().getHeaders().getValuesMap());
        requestContext.setClientIP(getCanonicalClientIpAddress());
        requestContext.setCookies(getRequest().getCookies().getValuesMap());
        resolver.setContext(requestContext);

        // Determine the format of the source image.
        Format format = Format.UNKNOWN;
        try {
            format = resolver.getSourceFormat();
        } catch (FileNotFoundException e) { // this needs to be rethrown
            if (Configuration.getInstance().
                    getBoolean(Key.CACHE_SERVER_PURGE_MISSING, false)) {
                // If the image was not found, purge it from the cache.
                new CacheFacade().purgeAsync(identifier);
            }
            throw e;
        }

        // Obtain an instance of the processor assigned to that format.
        final Processor processor = new ProcessorFactory().newProcessor(format);

        // Connect it to the resolver.
        new ProcessorConnector(resolver, processor, identifier).connect();

        final ImageInfo imageInfo = new ImageInfoFactory().newImageInfo(
                getImageURI(), processor, getOrReadInfo(identifier, processor));

        getBufferedResponseHeaders().add("Link",
                String.format("<%s>;rel=\"profile\";", imageInfo.profile));

        commitCustomResponseHeaders();

        return new JSONRepresentation(imageInfo, getNegotiatedMediaType());
    }

    /**
     * @return Full image URI corresponding to the given identifier, respecting
     *         the X-Forwarded-* and {@link #PUBLIC_IDENTIFIER_HEADER} reverse
     *         proxy headers.
     */
    private String getImageURI() {
        final Series<Header> headers = getRequest().getHeaders();
        return getPublicRootRef(getRequest().getRootRef(), headers) +
                RestletApplication.IIIF_1_PATH + "/" +
                Reference.encode(getPublicIdentifier());
    }

    private MediaType getNegotiatedMediaType() {
        MediaType mediaType;
        // If the client has requested JSON-LD, set the content type to
        // that; otherwise set it to JSON.
        List<Preference<MediaType>> preferences = getRequest().getClientInfo().
                getAcceptedMediaTypes();
        if (preferences.get(0) != null && preferences.get(0).toString().
                startsWith("application/ld+json")) {
            mediaType = new MediaType("application/ld+json");
        } else {
            mediaType = new MediaType("application/json");
        }
        return mediaType;
    }

}
