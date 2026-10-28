# Modularization

Here you will explore the modularization strategy of the test-bed Android app.

### Why Multi-Module?

* Developers can work on specific sections of the application without slowing down other developers.
* Maintainability: All files are organized in their respective modules, making it easier to locate what we're looking for.
* Incremental Compilation: Modifying a file allows modularized apps to compile faster compared to monolithic apps.
* CI/CD processes run faster.

### Modules in the app

<center>
<img src="images/modules.png" width="600px" alt="Diagram showing basic modules" />
</center>

* The **app module** serves as the foundation for the application, encompassing app-level and scaffolding components that connect the rest of the codebase. It has dependencies on all feature modules and necessary core modules.
* **Feature modules** are designed to manage specific responsibilities within the app and are scoped accordingly. These modules remain distinct and self-contained. Classes that are only relevant to a single feature module should remain within it, whereas shared functionality should be moved to the appropriate core module. Feature modules are independent and **do not rely on other feature modules**, but only on the core modules they require.
* **Core modules** are shared library modules that house auxiliary code and specific dependencies meant to be utilized across various parts of the app. These modules may depend on other core modules but should not have dependencies on feature or app modules. Additionally, some core modules are designed to implement specific layers of the Clean Architecture framework, ensuring clear separation of concerns and promoting reusability throughout the codebase.
* **Miscellaneous modules**, such as :architecture and :utils, serve specific purposes and can be utilized by any other module.

<table>
  <tr>
   <td><strong>Name</strong>
   </td>
   <td><strong>Responsibilities</strong>
   </td>
   <td><strong>Key classes and good examples</strong>
   </td>
  </tr>
  <tr>
   <td><code>app</code>
   </td>
   <td>Integrates all the essential components required for the app to function seamlessly.
   </td>
   <td><code>MyApplication, MainActivity</code><br>
   </td>
  </tr>
  <tr>
   <td><code>feature:movie-catalog,</code><br>
   <code>feature:2</code><br>
   ...
   </td>
   <td>Handles functionality related to a specific feature or user journey, typically encompassing UI components and ViewModels that retrieve data from other modules.<br>
   </td>
   <td><code>MovieListScreen</code><br>
   <code>MovieListViewModel</code>
   </td>
  </tr>
   <tr>
   <td><code>core:bridge-di</code>
   </td>
   <td>A necessary module to support dependency injection and prevent cyclic Gradle dependencies.
   </td>
   <td><code>CoreModule</code><br>
   </td>
  </tr>
  <tr>
   <td><code>core:data</code>
   </td>
   <td>Retrieving application data from various sources, utilized across different features.
   </td>
   <td><code>MoviesRepositoryImpl</code><br>
   </td>
  </tr>
  <tr>
   <td><code>core:database</code>
   </td>
   <td>A package that includes database-related modules provides both an API and concrete implementations. This approach ensures that other modules interact only with the API, without direct access to specific implementations such as the Room database.
   </td>
   <td><code>DatabaseDataSource, RoomDataSource</code><br>
   </td>
  </tr>
  <tr>
   <td><code>core:domain</code>
   </td>
   <td>Contains the app’s business logic in the form of use case classes, along with repository interfaces only, keeping the implementation details abstracted. 
   </td>
   <td><code>GetMoviesUseCase, MoviesRepository</code><br>
   </td>
  </tr>
  <tr>
   <td><code>core:model</code>
   </td>
   <td>Contains the app’s domain or business objects.  
   </td>
   <td><code>Movie, ErrorModel</code><br>
   </td>
  </tr>
  <tr>
   <td><code>core:navigation</code>
   </td>
   <td>A package that includes navigation-related modules provides both an API and concrete implementations. This setup ensures that the rest of the app interacts with a navigation API, while the actual implementation leverages a navigation library (such as Compose Navigation). This abstraction makes it easier to switch to a different navigation library in the future if needed. 
   </td>
   <td><code>Navigator, AndroidComposeNavigator</code><br>
   </td>
  </tr>
  <tr>
   <td><code>core:network</code>
   </td>
   <td>A package that includes network-related modules provides both an API and concrete implementation. This approach ensures that other modules interact only with the API, without direct access to specific implementations such as the Retrofit library.
   </td>
   <td><code>NetworkDataSource, RetrofitNetworkDataSource</code><br>
   </td>
  </tr>
  <tr>
   <td><code>core:presentation</code>
   </td>
   <td>A package that includes presentation-related module like MVI implementation, theme and common UI components.
   </td>
   <td><code>MviViewModel, Theme.kt</code><br>
   </td>
  </tr>
  <tr>
   <td><code>architecture</code>
   </td>
   <td>A package that includes architecture-related modules provides base classes that are utilized consistently throughout the app, promoting a uniform structure and reducing redundancy.
   </td>
   <td><code>ApiToDomainMapper</code><br>
   </td>
  </tr>
  <tr>
   <td><code>utils</code>
   </td>
   <td>A package that includes utility-related modules provides classes that are used throughout the app.
   </td>
   <td><code>random/Utils</code><br>
   </td>
  </tr>
  
</table>

