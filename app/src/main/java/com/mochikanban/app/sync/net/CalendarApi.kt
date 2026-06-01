package com.mochikanban.app.sync.net

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CalendarApi {

    @GET("users/me/calendarList")
    suspend fun listCalendars(
        @Header("Authorization") authorization: String,
    ): Response<CalendarListResponse>

    @GET("calendars/{calendarId}/events")
    suspend fun listEvents(
        @Header("Authorization") authorization: String,
        @Path("calendarId") calendarId: String,
        @Query("timeMin") timeMin: String? = null,
        @Query("timeMax") timeMax: String? = null,
        @Query("singleEvents") singleEvents: Boolean = true,
        @Query("showDeleted") showDeleted: Boolean = true,
        @Query("pageToken") pageToken: String? = null,
        @Query("syncToken") syncToken: String? = null,
        @Query("maxResults") maxResults: Int = 250,
    ): Response<EventListResponse>

    @POST("calendars/{calendarId}/events")
    suspend fun createEvent(
        @Header("Authorization") authorization: String,
        @Path("calendarId") calendarId: String,
        @Body event: EventDto,
    ): Response<EventDto>

    @PATCH("calendars/{calendarId}/events/{eventId}")
    suspend fun patchEvent(
        @Header("Authorization") authorization: String,
        @Path("calendarId") calendarId: String,
        @Path("eventId") eventId: String,
        @Body event: EventDto,
    ): Response<EventDto>

    @DELETE("calendars/{calendarId}/events/{eventId}")
    suspend fun deleteEvent(
        @Header("Authorization") authorization: String,
        @Path("calendarId") calendarId: String,
        @Path("eventId") eventId: String,
    ): Response<Unit>
}
