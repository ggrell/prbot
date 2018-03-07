package com.gyurigrell.prbot

import org.kohsuke.github.GitHub
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class MyApplication {
    private val log = LoggerFactory.getLogger(MyApplication::class.java)

    @Bean
    fun provideGitHub(@Value("\${github.endpoint}") endpoint: String,
                      @Value("\${github.user}") login: String,
                      @Value("\${github.oauthToken}") oauthToken: String): GitHub {
        return GitHub.connectToEnterpriseWithOAuth(endpoint, login, oauthToken)
    }

    @Bean
    fun init() = CommandLineRunner {
        log.debug("Initializing application")
    }
}

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args)
}
