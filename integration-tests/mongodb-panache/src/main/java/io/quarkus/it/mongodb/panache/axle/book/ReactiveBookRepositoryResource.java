package io.quarkus.it.mongodb.panache.axle.book;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.bson.types.ObjectId;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.SseElementType;
import org.reactivestreams.Publisher;

import io.quarkus.it.mongodb.panache.book.Book;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;

@Path("/axle/books/repository")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReactiveBookRepositoryResource {
    private static final Logger LOGGER = Logger.getLogger(ReactiveBookRepositoryResource.class);
    @Inject
    ReactiveBookRepository reactiveBookRepository;

    @PostConstruct
    void init() {
        String databaseName = reactiveBookRepository.mongoDatabase().getName();
        String collectionName = reactiveBookRepository.mongoCollection().getNamespace().getCollectionName();
        LOGGER.infov("Using BookRepository[database={0}, collection={1}]", databaseName, collectionName);
    }

    @GET
    public CompletionStage<List<Book>> getBooks(@QueryParam("sort") String sort) {
        if (sort != null) {
            return reactiveBookRepository.listAll(Sort.ascending(sort));
        }
        return reactiveBookRepository.listAll();
    }

    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @SseElementType(MediaType.APPLICATION_JSON)
    public Publisher<Book> streamBooks(@QueryParam("sort") String sort) {
        if (sort != null) {
            return reactiveBookRepository.streamAll(Sort.ascending(sort));
        }
        return reactiveBookRepository.streamAll();
    }

    @POST
    public CompletionStage<Response> addBook(Book book) {
        return reactiveBookRepository.persist(book).thenApply(v -> {
            //the ID is populated before sending it to the database
            String id = book.getId().toString();
            return Response.created(URI.create("/books/entity" + id)).build();
        });
    }

    @PUT
    public CompletionStage<Response> updateBook(Book book) {
        return reactiveBookRepository.update(book).thenApply(v -> Response.accepted().build());
    }

    // PATCH is not correct here but it allows to test persistOrUpdate without a specific subpath
    @PATCH
    public CompletionStage<Response> upsertBook(Book book) {
        return reactiveBookRepository.persistOrUpdate(book).thenApply(v -> Response.accepted().build());
    }

    @DELETE
    @Path("/{id}")
    public CompletionStage<Void> deleteBook(@PathParam("id") String id) {
        return reactiveBookRepository.findById(new ObjectId(id)).thenCompose(book -> reactiveBookRepository.delete(book));
    }

    @GET
    @Path("/{id}")
    public CompletionStage<Book> getBook(@PathParam("id") String id) {
        return reactiveBookRepository.findById(new ObjectId(id));
    }

    @GET
    @Path("/search/{author}")
    public CompletionStage<List<Book>> getBooksByAuthor(@PathParam("author") String author) {
        return reactiveBookRepository.list("author", author);
    }

    @GET
    @Path("/search")
    public CompletionStage<Book> search(@QueryParam("author") String author, @QueryParam("title") String title,
            @QueryParam("dateFrom") String dateFrom, @QueryParam("dateTo") String dateTo) {
        if (author != null) {
            return reactiveBookRepository.find("{'author': ?1,'bookTitle': ?2}", author, title).firstResult();
        }

        return reactiveBookRepository
                .find("{'creationDate': {$gte: ?1}, 'creationDate': {$lte: ?2}}", LocalDate.parse(dateFrom),
                        LocalDate.parse(dateTo))
                .firstResult();
    }

    @GET
    @Path("/search2")
    public CompletionStage<Book> search2(@QueryParam("author") String author, @QueryParam("title") String title,
            @QueryParam("dateFrom") String dateFrom, @QueryParam("dateTo") String dateTo) {
        if (author != null) {
            return reactiveBookRepository.find("{'author': :author,'bookTitle': :title}",
                    Parameters.with("author", author).and("title", title)).firstResult();
        }

        return reactiveBookRepository.find("{'creationDate': {$gte: :dateFrom}, 'creationDate': {$lte: :dateTo}}",
                Parameters.with("dateFrom", LocalDate.parse(dateFrom)).and("dateTo", LocalDate.parse(dateTo))).firstResult();
    }

    @DELETE
    public CompletionStage<Void> deleteAll() {
        return reactiveBookRepository.deleteAll().thenApply(l -> null);
    }
}
