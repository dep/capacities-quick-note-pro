package com.dnnypck.capacitiesquicknotepro.ui.main.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ContentTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Content") },
        placeholder = { Text("Enter text to share") },
        minLines = 5,
        maxLines = 10,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
    )
}
