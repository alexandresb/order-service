package com.polarbookshop.orderservice.event;

//message reçu par order-service notifiant que la commande a été "dispatchée"
public record OrderDispatchedMessage(
       Long orderId
) {}
