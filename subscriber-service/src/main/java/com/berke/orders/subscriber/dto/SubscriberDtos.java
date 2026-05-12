package com.berke.orders.subscriber.dto;import java.util.*;public class SubscriberDtos{
 public record ProductCommand(Long orderId,String customerId,List<ProductCommandItem> items){}
 public record ProductCommandItem(String sourceProductCode,String targetProductCode,String sourceItemRef,String productType){}
 public record ProductResult(Long orderId,String customerId,boolean success,String errorMessage,List<ProductResultItem> items){}
 public record ProductResultItem(String sourceProductCode,String targetProductCode,String sourceItemRef,String targetItemRef,String productType){}
 public record CustomerCommand(Long requestId,String customerId,String firstName,String lastName){}
 public record CustomerResult(Long requestId,String customerId,boolean success,String errorMessage){}
}
