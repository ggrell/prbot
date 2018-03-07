package com.gyurigrell.prbot

import org.kohsuke.github.*
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.io.StringReader

private const val MERGE_PREVENTER_BOT_NAME = "Merge Bot"
private const val WIP_LABEL = "WIP"
private val MERGE_PREVENTER_LABEL_NAMES = listOf(WIP_LABEL, "DO NOT MERGE")

@RestController
class GithubWebhookController(
//        val secret: String = "" /*System.getenv("SECRET_KEY")*/,
        val github: GitHub) {
    private val log = LoggerFactory.getLogger(GithubWebhookController::class.java)

    private val reviewedStates: Array<GHPullRequestReviewState> by lazy {
        arrayOf(GHPullRequestReviewState.APPROVED, GHPullRequestReviewState.CHANGES_REQUESTED)
    }

    @PostMapping("/github-webhook")
    fun webhook(@RequestHeader("X-GitHub-Event") event: String,
                @RequestBody payload: String): ResponseEntity<Unit> {

        val payloadReader = StringReader(payload)
        return when (event) {
            "pull_request" -> processPullRequest(payloadReader)
            "pull_request_review" -> processPullRequestReview(payloadReader)
            "ping" -> processPing(payloadReader)
            else -> ResponseEntity.ok().build()
        }
    }

    private fun processPullRequest(payloadReader: StringReader): ResponseEntity<Unit> {
        val eventPayload = github.parseEventPayload(payloadReader, GHEventPayload.PullRequest::class.java)
        val pullRequest = eventPayload.pullRequest
        val labels = pullRequest.labels.map { it.name }.toMutableList()
        when (eventPayload.action) {
            "opened", "edited", "synchronize" -> {
                if (pullRequest.title.contains("[WIP]")) {
                    if (!labels.contains(WIP_LABEL)) {
                        labels.add(WIP_LABEL)
                        pullRequest.setLabels(*labels.toTypedArray())
                        updateLatestCommitStatus(eventPayload.repository, pullRequest)
                    }
                } else {
                    if (labels.contains(WIP_LABEL)) {
                        labels.remove(WIP_LABEL)
                        pullRequest.setLabels(*labels.toTypedArray())
                        updateLatestCommitStatus(eventPayload.repository, pullRequest)
                    }
                }
            }

            "labeled", "unlabeled" -> {
                updateLatestCommitStatus(eventPayload.repository, pullRequest)
            }

            "review_requested" -> {
                if (!labels.contains("REVIEW REQUESTED")) {
                    labels.add("REVIEW REQUESTED")
                    eventPayload.pullRequest.setLabels(*labels.toTypedArray())
                }
            }
        }
        return ResponseEntity.ok().build()
    }

    private fun processPullRequestReview(payloadReader: StringReader): ResponseEntity<Unit> {
        val eventPayload = github.parseEventPayload(payloadReader, GHEventPayload.PullRequestReview::class.java)
        val pullRequest = eventPayload.pullRequest
        val labels = pullRequest.labels.map { it.name }.toMutableList()
        when (eventPayload.action) {
            "submitted" -> { // If a review approved or requested changes, remove the "REVIEW REQUESTED" label.
                if (reviewedStates.contains(eventPayload.review.state) || labels.contains("REVIEW REQUESTED")) {
                    labels.remove("REVIEW REQUESTED")
                    eventPayload.pullRequest.setLabels(*labels.toTypedArray())
                }
            }

            "dismissed" -> { // If a review was dismissed, and there are no more reviews, then add "REVIEW REQUESTED" label.
                val reviewCount = pullRequest.listReviews().count()
                if (reviewCount == 0) {
                    if (!labels.contains("REVIEW REQUESTED")) {
                        labels.add("REVIEW REQUESTED")
                        eventPayload.pullRequest.setLabels(*labels.toTypedArray())
                    }
                }
            }
        }
        return ResponseEntity.ok().build()
    }

    private fun processPing(payloadReader: StringReader): ResponseEntity<Unit> {
        val eventPayload = github.parseEventPayload(payloadReader, GHEventPayload.Ping::class.java)
        log.debug(eventPayload.toString())
        return ResponseEntity.accepted().build()
    }

    private fun updateLatestCommitStatus(repository: GHRepository, pullRequest: GHPullRequest) {
        val labels = pullRequest.labels.map { it.name }
        val lastCommit = pullRequest.listCommits().last()
        if (labels.any { MERGE_PREVENTER_LABEL_NAMES.contains(it) }) {
            repository.createCommitStatus(lastCommit.sha, GHCommitState.FAILURE, null,
                    "Can't merge due to a label from: ${MERGE_PREVENTER_LABEL_NAMES}",
                    MERGE_PREVENTER_BOT_NAME)
        } else {
            repository.createCommitStatus(lastCommit.sha, GHCommitState.SUCCESS, null,
                    "Merge Bot says: \"OK by me!\"", MERGE_PREVENTER_BOT_NAME)
        }
    }
}