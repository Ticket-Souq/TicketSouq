package org.ticketsouq.userservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Tag(name = "User", description = "Manages user accounts.")
public class UserPublicController {

    // TODO make endpoint change org status (PENDING => approve )
    // TODO make endpoint change org status to BANNED

    // TODO make org head can add agents , consumers
    ///  (send request to api getway have role , revieve credintiona )



}
