package com.example.uberclone.di

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.database
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object ModuleApplication {
    @Provides
    fun provideFireBaseAuth() = Firebase.auth

    @Provides
    fun provideFireBaseDatabase() = Firebase.database
}