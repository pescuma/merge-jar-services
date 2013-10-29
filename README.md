merge-jar-services
==================

Ant task to merge services inside jar files

To use, first register the task:
```
<taskdef resource="org/pescuma/mergeservices/antlib.xml" classpath="lib/merge-services.jar" />
```

Then call it passing the jars to load the a folder to store the resuts:

```
<merge-services dest="${services.dir}">
	<fileset dir="build/dist">
		<include name="**/*.jar" />
		<exclude name="**/*-source.jar" />
		<exclude name="**/*-tests.jar" />
	</fileset>
	<fileset dir="lib">
		<include name="**/*.jar" />
		<exclude name="**/*-source.jar" />
	</fileset>
</merge-services>
```
