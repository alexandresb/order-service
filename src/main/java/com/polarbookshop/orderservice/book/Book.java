package com.polarbookshop.orderservice.book;

//fait office de DTO
public record Book(
        String isbn,
        String title,
        String author,
        Double price
) {}
