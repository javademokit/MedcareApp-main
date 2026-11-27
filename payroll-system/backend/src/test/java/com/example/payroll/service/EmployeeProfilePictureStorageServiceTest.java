package com.example.payroll.service;

import com.example.payroll.model.EmployeeProfilePicture;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeProfilePictureStorageServiceTest {
    private static final byte[] PNG_CONTENT = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01, 0x02,
    };

    @Test
    void storesAndLoadsProfilePictureBytesFromMongo() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.save(any(EmployeeProfilePicture.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        EmployeeProfilePictureStorageService service = new EmployeeProfilePictureStorageService(mongo);
        MockMultipartFile upload = new MockMultipartFile(
                "file", "profile.png", "image/png", PNG_CONTENT);

        var metadata = service.save(11L, upload);

        org.mockito.ArgumentCaptor<EmployeeProfilePicture> pictureCaptor =
                org.mockito.ArgumentCaptor.forClass(EmployeeProfilePicture.class);
        verify(mongo).save(pictureCaptor.capture());
        EmployeeProfilePicture stored = pictureCaptor.getValue();
        assertEquals(11L, stored.getEmployeeId());
        assertEquals("image/png", metadata.contentType());
        assertArrayEquals(PNG_CONTENT, stored.getContent());
        when(mongo.findById(11L, EmployeeProfilePicture.class)).thenReturn(stored);

        var loaded = service.load(11L);

        assertEquals("image/png", loaded.contentType());
        assertArrayEquals(PNG_CONTENT, loaded.content());
    }

    @Test
    void rejectsFilesThatAreNotPngOrJpegImages() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        EmployeeProfilePictureStorageService service = new EmployeeProfilePictureStorageService(mongo);
        MockMultipartFile upload = new MockMultipartFile(
                "file", "profile.txt", "text/plain", "not an image".getBytes());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class, () -> service.save(11L, upload));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(mongo, never()).save(any(EmployeeProfilePicture.class));
    }
}
