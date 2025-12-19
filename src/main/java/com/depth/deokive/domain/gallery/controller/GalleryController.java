// package com.depth.deokive.domain.gallery.controller;
//
// import com.depth.deokive.domain.gallery.dto.GalleryDto;
// import com.depth.deokive.domain.gallery.repository.GalleryRepository;
// import lombok.RequiredArgsConstructor;
// import org.springframework.data.domain.Page;
// import org.springframework.data.domain.Pageable;
// import org.springframework.data.web.PageableDefault;
// import org.springframework.http.ResponseEntity;
// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.bind.annotation.PathVariable;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RestController;
//
// @RestController
// @RequiredArgsConstructor
// @RequestMapping("/api/v1/gallery")
// public class GalleryController {
//
//     private final GalleryRepository galleryRepository;
//
//     @GetMapping("/{archiveId}")
//     public ResponseEntity<Page<GalleryDto.Response>> getGalleries(
//             @PathVariable Long archiveId,
//             @PageableDefault(size = 5) Pageable pageable
//             ) {
//         return ResponseEntity.ok(
//                 galleryRepository.searchGalleries(archiveId, pageable)
//         );
//     }
// }
