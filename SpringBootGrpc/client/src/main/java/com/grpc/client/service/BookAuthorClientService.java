package com.grpc.client.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.google.protobuf.Descriptors;
import com.hs.Author;
import com.hs.Book;
import com.hs.BookAuthorServiceGrpc;
import com.shared.proto.TempDB;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;

@Service
public class BookAuthorClientService {

	@GrpcClient("grpc-service")
	BookAuthorServiceGrpc.BookAuthorServiceBlockingStub synchronousClient;

	@GrpcClient("grpc-service")
	BookAuthorServiceGrpc.BookAuthorServiceStub asynchronousClient;

	public Map<Descriptors.FieldDescriptor, Object> getAuthor(int authorId) {
		Author authorRequest = Author.newBuilder().setAuthorId(authorId).build();
		Author authorResponse = synchronousClient.getAuthor(authorRequest);
		return authorResponse.getAllFields();
	}

	public List<Map<Descriptors.FieldDescriptor, Object>> getBooksByAuthor(int authorId) throws InterruptedException {
		final Author authorRequest = Author.newBuilder().setAuthorId(authorId).build();
		final CountDownLatch countDownLatch = new CountDownLatch(1);
		final List<Map<Descriptors.FieldDescriptor, Object>> response = new ArrayList<>();
		asynchronousClient.getBooksByAuthor(authorRequest, new StreamObserver<Book>() {
			@Override
			public void onNext(Book book) {
				response.add(book.getAllFields());
			}

			@Override
			public void onError(Throwable throwable) {
				countDownLatch.countDown();
			}

			@Override
			public void onCompleted() {
				countDownLatch.countDown();
			}
		});
		boolean await = countDownLatch.await(5, TimeUnit.SECONDS);
		return await ? response : Collections.emptyList();
	}

	public Map<String, Map<Descriptors.FieldDescriptor, Object>> getExpensiveBook() throws InterruptedException {
		final CountDownLatch countDownLatch = new CountDownLatch(1);
		final Map<String, Map<Descriptors.FieldDescriptor, Object>> response = new HashMap<>();
		StreamObserver<Book> responseObserver = asynchronousClient.getExpensiveBook(new StreamObserver<Book>() {
			@Override
			public void onNext(Book book) {
				response.put("Expensive Book", book.getAllFields());
			}

			@Override
			public void onError(Throwable throwable) {
				countDownLatch.countDown();
			}

			@Override
			public void onCompleted() {
				countDownLatch.countDown();
			}
		});
		TempDB.getBooksFromTempDb().forEach(responseObserver::onNext);
		responseObserver.onCompleted();
		boolean await = countDownLatch.await(5, TimeUnit.SECONDS);
		return await ? response : Collections.emptyMap();
	}

	public List<Map<Descriptors.FieldDescriptor, Object>> getBooksByGender(String gender) throws InterruptedException {
		final CountDownLatch countDownLatch = new CountDownLatch(1);
		final List<Map<Descriptors.FieldDescriptor, Object>> response = new ArrayList<>();

		StreamObserver<Book> responseObserver = asynchronousClient.getBooksByGender(new StreamObserver<Book>() {
			@Override
			public void onNext(Book book) {
				response.add(book.getAllFields());
			}

			@Override
			public void onError(Throwable throwable) {
				countDownLatch.countDown();
			}

			@Override
			public void onCompleted() {
				countDownLatch.countDown();
			}
		});

		TempDB.getAuthorsFromTempDb().stream().filter(author -> {
			boolean match = author.getGender().trim().equalsIgnoreCase(gender.trim());
			return match;
		}).forEach(author -> {
			Book book = Book.newBuilder().setAuthorId(author.getAuthorId()).build();
			responseObserver.onNext(book);
		});

		responseObserver.onCompleted();
		boolean await = countDownLatch.await(5, TimeUnit.SECONDS);
		return await ? response : Collections.emptyList();
	}

}
