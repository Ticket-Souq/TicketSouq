package org.ticketsouq.userservice.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketsouq.sharedmodule.ApiGateway.dto.CreateUserRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/private/user")
@RequiredArgsConstructor
@Hidden
public class UserPrivateController {

    // TODO implement this
    /**
        this endpoint firstly receive a requset form auth-service
        to create new recored in user table but
        when register new user
            - check if orgName ==null
                - create new user and return
            - else
                - check if orgName exist in db
                    - create new user
                    - create new recorde in Member table with orgNmae id and user id
                    - return {200 if ok / busniuss exp if creation falild }
                - else
                    - create new org in org table
                     - create new user
                     - create new recorde in Member table with orgNmae id and user id return
     */
    @PostMapping
    public ResponseEntity<Void> register(@RequestBody CreateUserRequest request) {
//        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
        return ResponseEntity.ok().build();
    }
    // TODO create agent/consumer request

    @GetMapping("/isbanned")
    public ResponseEntity<Boolean> isBelongToBannedOrg(@RequestBody UUID userId){
        return ResponseEntity.ok().body(false);
    }
}
