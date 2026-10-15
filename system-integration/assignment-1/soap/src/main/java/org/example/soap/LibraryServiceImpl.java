package org.example.soap;

import jakarta.jws.WebService;
import org.example.soap.library.Author;
import org.example.soap.library.AuthorList;
import org.example.soap.library.Book;
import org.example.soap.library.CreateAuthorRequest;
import org.example.soap.library.CreateBookRequest;
import org.example.soap.library.CreatePublishingCompanyRequest;
import org.example.soap.library.EmptyRequest;
import org.example.soap.library.EntityIdRequest;
import org.example.soap.library.GeneratedIdResponse;
import org.example.soap.library.OperationAcknowledgement;
import org.example.soap.library.PublishingCompany;
import org.example.soap.library.PublishingCompanyList;
import org.example.soap.library.UpdateAuthorRequest;
import org.example.soap.library.UpdateBookRequest;
import org.example.soap.library.UpdatePublishingCompanyRequest;
import org.example.soap.library.service.LibraryPortType;
import org.example.soap.library.service.NotFoundFault;
import org.example.soap.library.service.ConflictFault;
import org.example.soap.library.service.ValidationFault;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@WebService(
        serviceName = "LibraryService",
        portName = "LibraryPort",
        endpointInterface = "org.example.soap.library.service.LibraryPortType",
        targetNamespace = "http://soap.example.org/library/service")
public class LibraryServiceImpl implements LibraryPortType {

    // Midlertidig in-memory lagring; data forsvinder, når serveren stoppes.
    private final Map<Integer, Book> books = new ConcurrentHashMap<>();
    private final Map<Integer, Author> authors = new ConcurrentHashMap<>();
    private final Map<Integer, PublishingCompany> publishingCompanies = new ConcurrentHashMap<>();
    // AtomicInteger sikrer unikke ID'er ved samtidige requests.
    private final AtomicInteger nextId = new AtomicInteger(1);

    // -------------------- Book-operationer --------------------

    @Override
    public GeneratedIdResponse createBook(CreateBookRequest request) throws ValidationFault {
        // En bog kræver en titel og gyldige referencer til author og publishing company.
        require(request != null, "request", "Request is required");
        requireText(request.getTitle(), "title");
        requireReference(authors, request.getAuthorId(), "authorId");
        requireReference(publishingCompanies, request.getPublishingCompanyId(),
                "publishingCompanyId");
        Book book = new Book();
        book.setId(nextId.getAndIncrement());
        book.setTitle(request.getTitle());
        book.setAuthorId(request.getAuthorId());
        book.setPublishingCompanyId(request.getPublishingCompanyId());
        book.setPublishingYear(request.getPublishingYear());
        books.put(book.getId(), book);
        return idResponse(book.getId());
    }

    @Override
    public Book getBookById(EntityIdRequest request) throws NotFoundFault {
        // find kaster NotFoundFault, hvis ID'et ikke findes.
        return find(books, request, "Book");
    }

    @Override
    public OperationAcknowledgement updateBook(UpdateBookRequest request)
            throws ValidationFault, NotFoundFault {
        // Før opdatering kontrolleres både ID, felter og fremmednøgler.
        require(request != null, "request", "Request is required");
        Book book = find(books, request.getId(), "Book");
        requireText(request.getTitle(), "title");
        requireReference(authors, request.getAuthorId(), "authorId");
        requireReference(publishingCompanies, request.getPublishingCompanyId(),
                "publishingCompanyId");
        book.setTitle(request.getTitle());
        book.setAuthorId(request.getAuthorId());
        book.setPublishingCompanyId(request.getPublishingCompanyId());
        book.setPublishingYear(request.getPublishingYear());
        return acknowledgement("Book updated");
    }

    @Override
    public OperationAcknowledgement deleteBook(EntityIdRequest request) throws NotFoundFault {
        // En bog kan slettes direkte, hvis den eksisterer.
        int id = id(request);
        if (books.remove(id) == null) {
            throw notFound("Book", id);
        }
        return acknowledgement("Book deleted");
    }

    // -------------------- Author-operationer --------------------

    @Override
    public GeneratedIdResponse createAuthor(CreateAuthorRequest request) throws ValidationFault {
        require(request != null, "request", "Request is required");
        requireText(request.getName(), "name");
        requireText(request.getSurname(), "surname");
        Author author = new Author();
        author.setId(nextId.getAndIncrement());
        author.setName(request.getName());
        author.setSurname(request.getSurname());
        authors.put(author.getId(), author);
        return idResponse(author.getId());
    }

    @Override
    public Author getAuthorById(EntityIdRequest request) throws NotFoundFault {
        return find(authors, request, "Author");
    }

    @Override
    public AuthorList listAuthors(EmptyRequest request) {
        // SOAP-requesten er tom; alle forfattere returneres i en liste.
        AuthorList result = new AuthorList();
        result.getAuthor().addAll(authors.values());
        return result;
    }

    @Override
    public OperationAcknowledgement updateAuthor(UpdateAuthorRequest request)
            throws ValidationFault, NotFoundFault {
        require(request != null, "request", "Request is required");
        Author author = find(authors, request.getId(), "Author");
        requireText(request.getName(), "name");
        requireText(request.getSurname(), "surname");
        author.setName(request.getName());
        author.setSurname(request.getSurname());
        return acknowledgement("Author updated");
    }

    @Override
    public OperationAcknowledgement deleteAuthor(EntityIdRequest request)
            throws ConflictFault, NotFoundFault {
        int id = id(request);
        find(authors, id, "Author");
        // En forfatter må ikke slettes, hvis en bog stadig refererer til den.
        if (books.values().stream().anyMatch(book -> book.getAuthorId() == id)) {
            throw conflict("Author", id, "Book");
        }
        authors.remove(id);
        return acknowledgement("Author deleted");
    }

    // -------------------- PublishingCompany-operationer --------------------

    @Override
    public GeneratedIdResponse createPublishingCompany(
            CreatePublishingCompanyRequest request) throws ValidationFault {
        require(request != null, "request", "Request is required");
        requireText(request.getName(), "name");
        PublishingCompany company = new PublishingCompany();
        company.setId(nextId.getAndIncrement());
        company.setName(request.getName());
        publishingCompanies.put(company.getId(), company);
        return idResponse(company.getId());
    }

    @Override
    public PublishingCompany getPublishingCompanyById(EntityIdRequest request)
            throws NotFoundFault {
        return find(publishingCompanies, request, "PublishingCompany");
    }

    @Override
    public PublishingCompanyList listPublishingCompanies(EmptyRequest request) {
        // SOAP-requesten er tom; alle forlag returneres i en liste.
        PublishingCompanyList result = new PublishingCompanyList();
        result.getPublishingCompany().addAll(publishingCompanies.values());
        return result;
    }

    @Override
    public OperationAcknowledgement updatePublishingCompany(
            UpdatePublishingCompanyRequest request) throws ValidationFault, NotFoundFault {
        require(request != null, "request", "Request is required");
        PublishingCompany company = find(publishingCompanies, request.getId(),
                "PublishingCompany");
        requireText(request.getName(), "name");
        company.setName(request.getName());
        return acknowledgement("Publishing company updated");
    }

    @Override
    public OperationAcknowledgement deletePublishingCompany(EntityIdRequest request)
            throws ConflictFault, NotFoundFault {
        int id = id(request);
        find(publishingCompanies, id, "PublishingCompany");
        // Et forlag må ikke slettes, hvis en bog stadig refererer til det.
        if (books.values().stream().anyMatch(book -> book.getPublishingCompanyId() == id)) {
            throw conflict("PublishingCompany", id, "Book");
        }
        publishingCompanies.remove(id);
        return acknowledgement("Publishing company deleted");
    }

    // -------------------- Fælles hjælpefunktioner --------------------

    // Uddrager ID'et fra en request og afviser ugyldige ID'er.
    private static int id(EntityIdRequest request) throws NotFoundFault {
        if (request == null || request.getId() < 1) {
            throw notFound("Entity", request == null ? 0 : request.getId());
        }
        return request.getId();
    }

    private static <T> T find(Map<Integer, T> values, EntityIdRequest request, String type)
            throws NotFoundFault {
        return find(values, id(request), type);
    }

    private static <T> T find(Map<Integer, T> values, int id, String type)
            throws NotFoundFault {
        T value = values.get(id);
        if (value == null) {
            throw notFound(type, id);
        }
        return value;
    }

    private static void require(boolean condition, String field, String message)
            throws ValidationFault {
        if (!condition) {
            throw validation(field, message);
        }
    }

    private static void requireText(String value, String field) throws ValidationFault {
        require(value != null && !value.isBlank(), field, field + " is required");
    }

    private static <T> void requireReference(Map<Integer, T> values, int id, String field)
            throws ValidationFault {
        require(id > 0 && values.containsKey(id), field,
                field + " must reference an existing entity");
    }

    private static GeneratedIdResponse idResponse(int id) {
        // Create-operationer returnerer det genererede ID i denne response-type.
        GeneratedIdResponse response = new GeneratedIdResponse();
        response.setId(id);
        return response;
    }

    private static OperationAcknowledgement acknowledgement(String message) {
        // Update- og delete-operationer returnerer en fælles kvittering.
        OperationAcknowledgement response = new OperationAcknowledgement();
        response.setSuccess(true);
        response.setMessage(message);
        return response;
    }

    private static ValidationFault validation(String field, String message) {
        // SOAP-faulten indeholder både en teknisk kode og en forklaring.
        org.example.soap.library.ValidationFault detail =
                new org.example.soap.library.ValidationFault();
        detail.setCode("VALIDATION_ERROR");
        detail.setMessage(message);
        detail.setField(field);
        return new ValidationFault(message, detail);
    }

    private static NotFoundFault notFound(String type, int id) {
        // Bruges, når en klient refererer til en entitet, der ikke findes.
        org.example.soap.library.NotFoundFault detail =
                new org.example.soap.library.NotFoundFault();
        detail.setCode("NOT_FOUND");
        detail.setMessage(type + " with id " + id + " was not found");
        detail.setEntityType(type);
        detail.setEntityId(id);
        return new NotFoundFault(detail.getMessage(), detail);
    }

    private static ConflictFault conflict(String type, int id, String referencingType) {
        // Bruges, når en entitet ikke kan slettes pga. en eksisterende reference.
        org.example.soap.library.ConflictFault detail =
                new org.example.soap.library.ConflictFault();
        detail.setCode("CONFLICT");
        detail.setMessage(type + " is referenced by " + referencingType);
        detail.setEntityType(type);
        detail.setEntityId(id);
        detail.setReferencingEntityType(referencingType);
        return new ConflictFault(detail.getMessage(), detail);
    }
}
